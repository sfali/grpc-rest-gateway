package com.improving.grpc_rest_gateway.compiler

import com.google.api.AnnotationsProto
import com.google.api.HttpRule.PatternCase
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType
import com.google.protobuf.Descriptors.{
  Descriptor,
  EnumDescriptor,
  FieldDescriptor,
  FileDescriptor,
  MethodDescriptor,
  ServiceDescriptor
}
import com.google.protobuf.ExtensionRegistry
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import com.improving.grpc_rest_gateway.api.GrpcRestGatewayProto
import com.improving.grpc_rest_gateway.api.GrpcRestGatewayProto.StatusDescription
import protocgen.{CodeGenApp, CodeGenRequest, CodeGenResponse}
import scalapb.compiler.FunctionalPrinter.PrinterEndo
import scalapb.compiler.{DescriptorImplicits, FunctionalPrinter, NameUtils, ProtobufGenerator}
import scalapb.options.Scalapb

import scala.annotation.tailrec
import scala.collection.mutable
import scala.jdk.CollectionConverters.*

object OpenApiGenerator extends CodeGenApp {

  override def registerExtensions(registry: ExtensionRegistry): Unit = {
    Scalapb.registerAllExtensions(registry)
    AnnotationsProto.registerAllExtensions(registry)
    GrpcRestGatewayProto.registerAllExtensions(registry)
  }

  override def process(request: CodeGenRequest): CodeGenResponse =
    ProtobufGenerator.parseParameters(request.parameter) match {
      case Right(params) =>
        // Implicits gives you extension methods that provide ScalaPB names and types
        // for protobuf entities.
        val implicits = DescriptorImplicits.fromCodeGenRequest(params, request)

        CodeGenResponse.succeed(
          request
            .filesToGenerate
            .flatMap { fd =>
              val services = fd.getServices.asScala
              if (services.isEmpty || services.forall(getUnaryCallsWithHttpExtension(_).isEmpty)) None
              else Some(fd)
            }
            .map(fd => new OpenApiMessagePrinter(fd, implicits))
            .map(_.result)
        )

      case Left(error) => CodeGenResponse.fail(error)
    }

  private class OpenApiMessagePrinter(fd: FileDescriptor, implicits: DescriptorImplicits) {
    import implicits.*

    // map of services defined in this file to methods with `HTTP` annotations
    private val serviceDescriptorToMethodsMap = fd
      .getServices
      .asScala
      .flatMap { sd =>
        val methods = getUnaryCallsWithHttpExtension(sd)
        if (methods.isEmpty) None
        else Some(sd -> methods)
      }
      .toMap
    private val services = serviceDescriptorToMethodsMap.keys

    // maps of already traversed methods in order to avoid duplication
    private val componentsMap = mutable.Map.empty[String, Boolean].withDefaultValue(false)

    lazy val result: CodeGeneratorResponse.File = {
      val b = CodeGeneratorResponse.File.newBuilder()
      b.setName(s"${getProtoFileName(fd.getName)}.yml")
      b.setContent(content)
      b.build()
    }

    private val content =
      new FunctionalPrinter()
        .add("openapi: 3.1.0", "info:")
        .addIndented(
          """version: 0.1.0-SNAPSHOT""",
          s"""description: "REST API generated from ${fd.getName}"""",
          s"""title: "${fd.getFullName}""""
        )
        .add("tags:")
        .print(services) { case (p, service) => generateTag(service)(p) }
        .result()

    private def generateTag(service: ServiceDescriptor): PrinterEndo = { printer =>
      val esd = ExtendedServiceDescriptor(service)
      printer
        .indent
        .add(s"- name: ${esd.name}")
        .add(s"  description: ${esd.comment.map(_.trim).getOrElse(esd.name)}")
        .outdent
        .add("paths:")
        .indent
        .print(services) { case (p, service) => generatePaths(service)(p) }
        .outdent
        .add("components:")
        .addIndented("schemas:")
        .indent
        .print(services) { case (p, service) => generateComponentsForService(service)(p) }
        .outdent
    }

    // START OF PATHS SECTION

    private def getMethods(service: ServiceDescriptor) =
      service
        .getMethods
        .asScala
        .filter { m =>
          val options = m.toProto.getOptions
          // only unary calls with http method specified
          !m.isClientStreaming && !m.isServerStreaming && options.hasExtension(AnnotationsProto.http)
        }

    private def getPaths(service: ServiceDescriptor) =
      getMethods(service)
        .flatMap { method =>
          val paths = extractPaths(method)
          paths.map { tuple =>
            tuple -> method
          }
        }
        .foldLeft(Map.empty[String, Seq[(PatternCase, MethodDescriptor)]]) {
          case (result, ((patternCase, path, _), method)) =>
            val updatedValues =
              result.get(path) match {
                case Some(values) => values :+ ((patternCase, method))
                case None         => Seq.empty[(PatternCase, MethodDescriptor)] :+ ((patternCase, method))
              }
            result + (path -> updatedValues)
        }

    private def generatePaths(service: ServiceDescriptor): PrinterEndo =
      _.print(getPaths(service).toSeq.sortBy(_._1)) { case (p, (path, pathMethods)) =>
        generatePath(path, pathMethods.sortBy(_._1))(p)
      }

    private def generatePath(path: String, pathMethods: Seq[(PatternCase, MethodDescriptor)]): PrinterEndo =
      _.add(s"$path:")
        .indent
        .print(pathMethods) { case (p, (patternCase, methodDescriptor)) =>
          val printer = generateMethod(patternCase, path, methodDescriptor)(p)
          val statusDescriptions = getStatusDescriptions(methodDescriptor)
          generateResponses(methodDescriptor.getOutputType, statusDescriptions)(printer)
        }
        .outdent

    private def generateMethod(
      patternCase: PatternCase,
      path: String,
      methodDescriptor: MethodDescriptor
    ): PrinterEndo = _.call(generateMethod(path, patternCase, methodDescriptor))

    private def generateMethod(
      path: String,
      patternCase: PatternCase,
      methodDescriptor: MethodDescriptor
    ): PrinterEndo = { printer =>
      val pathElements = extractPathElements(path)
      val hasPathVariables = pathElements.nonEmpty
      val http = methodDescriptor.getOptions.getExtension(AnnotationsProto.http)
      lazy val maybeBody = findFieldByName(http.getBody, methodDescriptor)
      patternCase match {
        case PatternCase.GET =>
          printer
            .add("get:")
            .indent
            .call(generateMethodInfo(methodDescriptor))
            .call(generateParameters(methodDescriptor.getInputType, pathElements))
            .outdent
        case PatternCase.POST =>
          printer
            .add("post:")
            .indent
            .call(generateMethodInfo(methodDescriptor))
            .when(hasPathVariables)(
              _.call(generateParameters(methodDescriptor.getInputType, pathElements))
            )
            .when(hasPathVariables && maybeBody.isDefined)(_.call(generateBodyParameters(maybeBody.get.getName)))
            .when(!hasPathVariables)(_.call(generateBodyParameters(methodDescriptor.getInputType.getName)))
            .outdent
        case PatternCase.PUT =>
          printer
            .add("put:")
            .indent
            .call(generateMethodInfo(methodDescriptor))
            .when(hasPathVariables)(
              _.call(generateParameters(methodDescriptor.getInputType, pathElements))
            )
            .when(hasPathVariables && maybeBody.isDefined)(_.call(generateBodyParameters(maybeBody.get.getName)))
            .when(!hasPathVariables)(_.call(generateBodyParameters(methodDescriptor.getInputType.getName)))
            .outdent
        case PatternCase.DELETE =>
          printer
            .add("delete:")
            .indent
            .call(generateMethodInfo(methodDescriptor))
            .call(generateParameters(methodDescriptor.getInputType, pathElements))
            .outdent
        case _ => printer
      }
    }

    private def generateMethodInfo(m: MethodDescriptor): PrinterEndo = {
      val descriptor = ExtendedMethodDescriptor(m)
      val description = descriptor.comment.filterNot(_.isBlank).getOrElse(s"Generated from ${m.getName}").trim
      val indexOfSeparator = description.indexOf(".")
      val summary = if (indexOfSeparator > 0) description.substring(0, indexOfSeparator) else ""
      _.add("tags:")
        .addIndented(s"- ${m.getName}")
        .when(summary.nonEmpty && summary.length <= 15)(_.add(s"summary: $summary"))
        .add(s"description: $description")
    }

    private def generateParameters(
      inputType: Descriptor,
      pathElements: Seq[String],
      prefix: String = ""
    ): PrinterEndo =
      _.when(inputType.getFields.asScala.nonEmpty && prefix.isBlank)(
        _.add("parameters:")
          .indent
          .print(inputType.getFields.asScala) { case (p, f) =>
            p.call(generateParameter(f, pathElements, prefix))
          }
          .outdent
      )
        .when(inputType.getFields.asScala.nonEmpty && prefix.nonEmpty)(_.print(inputType.getFields.asScala) {
          case (p, f) =>
            p.call(generateParameter(f, pathElements, prefix))
        })

    private def generateParameter(
      field: FieldDescriptor,
      pathElements: Seq[String],
      prefix: String = ""
    ): PrinterEndo = { printer =>
      val fullPathName = NameUtils.snakeCaseToCamelCase(s"$prefix${field.upperScalaName}", upperInitial = true)
      val inPath = pathElements.contains(fullPathName)
      val inQuery = !inPath
      val explode = field.isRepeated && inQuery
      field.getJavaType match {
        case JavaType.MESSAGE =>
          val p = if (prefix.isEmpty) s"${field.getName}." else s"$prefix.${field.getName}."
          printer.call(generateParameters(field.getMessageType, pathElements, p))
        case JavaType.ENUM =>
          printer
            .add(s"- name: $prefix${field.getName}")
            .when(inPath)(
              _.addIndented("in: path", "required: true", "schema:")
                .indent
                .addIndented(s"""$$ref: "#/components/schemas/${field.getEnumType.getName}" """)
                .outdent
            )
            .when(inQuery)(
              _.addIndented("in: query", "schema:")
                .indent
                .addIndented(s"""$$ref: "#/components/schemas/${field.getEnumType.getName}" """)
                .outdent
            )
            .when(explode)(_.addIndented("explode: true"))
        case JavaType.INT =>
          printer
            .add(s"- name: $prefix${field.getName}")
            .when(inPath)(
              _.addIndented("in: path", "required: true", "schema:")
                .indent
                .addIndented("type: integer", "format: int32")
                .outdent
            )
            .when(inQuery)(
              _.addIndented("in: query", "schema:").indent.addIndented("type: integer", "format: int32").outdent
            )
            .when(explode)(_.addIndented("explode: true"))
        case JavaType.LONG =>
          printer
            .add(s"- name: $prefix${field.getName}")
            .when(inPath)(
              _.addIndented("in: path", "required: true", "schema:")
                .indent
                .addIndented("type: integer", "format: int64")
                .outdent
            )
            .when(inQuery)(
              _.addIndented("in: query", "schema:").indent.addIndented("type: integer", "format: int64").outdent
            )
            .when(explode)(_.addIndented("explode: true"))
        case JavaType.DOUBLE =>
          printer
            .add(s"- name: $prefix${field.getName}")
            .when(inPath)(
              _.addIndented("in: path", "required: true", "schema:")
                .indent
                .addIndented("type: number", "format: double")
                .outdent
            )
            .when(inQuery)(
              _.addIndented("in: query", "schema:").indent.addIndented("type: number", "format: double").outdent
            )
            .when(explode)(_.addIndented("explode: true"))
        case JavaType.FLOAT =>
          printer
            .add(s"- name: $prefix${field.getName}")
            .when(inPath)(
              _.addIndented("in: path", "required: true", "schema:")
                .indent
                .addIndented("type: number", "format: float")
                .outdent
            )
            .when(inQuery)(
              _.addIndented("in: query", "schema:").indent.addIndented("type: number", "format: float").outdent
            )
            .when(explode)(_.addIndented("explode: true"))
        case t =>
          printer
            .add(s"- name: $prefix${field.getName}")
            .when(inPath)(
              _.addIndented("in: path", "required: true", "schema:")
                .indent
                .addIndented(s"type: ${t.name.toLowerCase}")
                .outdent
            )
            .when(inQuery)(
              _.addIndented("in: query", "schema:").indent.addIndented(s"type: ${t.name.toLowerCase}").outdent
            )
            .when(explode)(_.addIndented("explode: true"))
      }
    }

    private def generateBodyParameters(name: String): PrinterEndo =
      _.add("requestBody:")
        .indent
        .add("content:")
        .addIndented("application/json:")
        .indent
        .addIndented("schema:")
        .indent
        .addIndented(s"""$$ref: "#/components/schemas/$name"""")
        .outdent
        .outdent
        .outdent

    private def generateResponses(
      outType: Descriptor,
      statusDescription: Seq[StatusDescription],
      prefix: String = ""
    ): PrinterEndo = { printer =>
      val successStatus = statusDescription.head
      val refName = outType.getName
      printer
        .indent
        .add("responses:")
        .indent
        .add(s""""${successStatus.getStatus}":""")
        .indent
        .add(s"description: ${successStatus.getDescription}")
        .when(successStatus.getStatus != 204 || refName != "Empty")(
          _.add("content:")
            .indent
            .add("application/json:")
            .indent
            .add("schema:")
            .indent
            .add(s"""$$ref: "#/components/schemas/$refName"""")
            .outdent
            .outdent
            .outdent
        )
        .outdent
        .print(statusDescription.tail) { case (p, statusDescription) => printStatus(statusDescription)(p) }
        .add("default:")
        .indent
        .add("description: Unexpected error")
        .outdent
        .outdent
        .outdent
    }

    private def printStatus(statusDescription: StatusDescription): PrinterEndo = { printer =>
      val status = statusDescription.getStatus
      val description = statusDescription.getDescription
      printer
        .add(s""""$status":""")
        .when(description.nonEmpty)(
          _.indent.add(s"description: $description").outdent
        )
        .when(description.isEmpty)(
          _.indent.add(s"description: Handle $status").outdent
        )
    }

    private def extractPathElements(path: String) =
      if (path.isBlank) Seq.empty[String]
      else {
        path
          .split("/")
          .filter(_.startsWith("{"))
          .map(_.replaceAll("\\{", "").replaceAll("}", ""))
          .map(name => NameUtils.snakeCaseToCamelCase(name = name, upperInitial = true))
          .toSeq
      }

    private def findFieldByName(fieldName: String, methodDescriptor: MethodDescriptor) =
      Option(methodDescriptor.getInputType.findFieldByName(fieldName))
        .map(_.getMessageType)
        .orElse(Option(methodDescriptor.getInputType.findNestedTypeByName(fieldName)))

    // END OF PATHS SECTION

    // START OF COMPONENTS SECTION

    private def generateComponentsForService(serviceDescriptor: ServiceDescriptor): PrinterEndo = { printer =>
      val methodDescriptors = serviceDescriptorToMethodsMap(serviceDescriptor)
      val (descriptors, enumDescriptors) =
        methodDescriptors.foldLeft((Seq.empty[Descriptor], Seq.empty[EnumDescriptor])) {
          case ((descriptors, enumDescriptors), md) =>
            val (d1, ed1) = extractDefs(md.getInputType)
            val (d2, ed2) = extractDefs(md.getOutputType)
            (descriptors ++ d1 ++ d2, enumDescriptors ++ ed1 ++ ed2)
        }

      // first process all message descriptors then enum descriptors
      printer
        .indent
        .print(descriptors) { case (p, descriptor) => generateObjectComponent(descriptor)(p) }
        .print(enumDescriptors) { case (p, enumDescriptor) => generateEnumComponent(enumDescriptor)(p) }
        .outdent
    }

    private def generateObjectComponent(descriptor: Descriptor): PrinterEndo = { printer =>
      if (componentsMap(descriptor.getFullName)) printer
      else {
        componentsMap += (descriptor.getFullName -> true)
        val fields = descriptor.getFields.asScala

        // no need to generate definition for wrapper type
        if (descriptor.scalaType.fullName.startsWith("com.google.protobuf.wrappers")) printer
        // no need to print map entry
        else if (descriptor.isMapEntry) printer
        else {
          printer
            .add(s"${descriptor.getName}:")
            .addIndented("type: object", "properties:")
            .indent
            .print(fields) { case (p, fd) =>
              if (fd.isMapField) {
                val messageType = fd.getMessageType
                val mapValue = messageType.getFields.asScala.last
                val mapValueType = mapValue.getJavaType
                p.indent
                  .add(s"${fd.getName}:")
                  .indent
                  .add("type: object", "additionalProperties:")
                  .call(generateDefinitionType(mapValue, mapValueType))
                  .outdent
                  .outdent
              } else if (fd.isRepeated) {
                p.indent
                  .add(fd.getName + ":")
                  .indent
                  .add("type: array", "items:")
                  .call(generateDefinitionType(fd, fd.getJavaType))
                  .outdent
                  .outdent
              } else p.indent.add(s"${fd.getName}:").call(generateDefinitionType(fd, fd.getJavaType)).outdent
            }
            .outdent
        }
      }
    }

    private def generateEnumComponent(enumDescriptor: EnumDescriptor): PrinterEndo = { printer =>
      if (componentsMap(enumDescriptor.getFullName)) printer
      else {
        componentsMap += (enumDescriptor.getFullName -> true)
        printer
          .add(s"${enumDescriptor.getName}:")
          .addIndented("type: string", "enum:")
          .indent
          .addIndented(enumDescriptor.getValues.asScala.toSeq.map(v => s"- ${v.getName}")*)
          .outdent
      }
    }

    @tailrec
    private def generateDefinitionType(fd: FieldDescriptor, valueJavaType: JavaType): PrinterEndo =
      valueJavaType match {
        case JavaType.INT    => _.addIndented("type: integer", "format: int32", getFieldDescription(fd))
        case JavaType.LONG   => _.addIndented("type: integer", "format: int64", getFieldDescription(fd))
        case JavaType.FLOAT  => _.addIndented("type: number", "format: float", getFieldDescription(fd))
        case JavaType.DOUBLE => _.addIndented("type: number", "format: double", getFieldDescription(fd))
        case JavaType.STRING => _.addIndented("type: string", getFieldDescription(fd))
        case JavaType.MESSAGE if fd.getMessageType.scalaType.fullName.startsWith("com.google.protobuf.wrappers") =>
          val primitiveField = fd.getMessageType.getFields.asScala.head
          generateDefinitionType(primitiveField, primitiveField.getJavaType)
        case JavaType.MESSAGE => _.addIndented(s"""$$ref: "#/components/schemas/${fd.getMessageType.getName}"""")
        case JavaType.ENUM    => _.addIndented(s"""$$ref: "#/components/schemas/${fd.getEnumType.getName}"""")
        case t                => _.addIndented(s"type: ${t.name.toLowerCase}")
      }

    private def getFieldDescription(field: FieldDescriptor) =
      s"description: ${field.comment.map(_.trim).getOrElse(field.getName)}"

    // extract all fields for types `MESSAGE` and `ENUM`, this will be used to build `components` section
    private def extractDefs(d: Descriptor): (Seq[Descriptor], Seq[EnumDescriptor]) =
      d.getFields.asScala.foldLeft((Seq.empty[Descriptor], Seq.empty[EnumDescriptor])) {
        case ((descriptors, enumDescriptors), fd) =>
          fd.getJavaType match {
            case JavaType.ENUM => (descriptors :+ d, enumDescriptors :+ fd.getEnumType)
            case JavaType.MESSAGE =>
              val messageType = fd.getMessageType
              val (r1, r2) = extractDefs(messageType)
              ((descriptors :+ d) ++ r1, enumDescriptors ++ r2)
            case _ => (descriptors :+ d, enumDescriptors)
          }
      }

    // END OF COMPONENTS SECTION
  }
}
