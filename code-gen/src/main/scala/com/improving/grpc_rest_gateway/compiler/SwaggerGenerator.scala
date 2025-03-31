package com.improving
package grpc_rest_gateway
package compiler

import com.google.api.AnnotationsProto
import com.google.api.HttpRule.PatternCase
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType
import com.google.protobuf.Descriptors.{Descriptor, FieldDescriptor, FileDescriptor, MethodDescriptor, ServiceDescriptor}
import com.google.protobuf.ExtensionRegistry
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import protocgen.{CodeGenApp, CodeGenRequest, CodeGenResponse}
import scalapb.compiler.FunctionalPrinter.PrinterEndo
import scalapb.compiler.{DescriptorImplicits, FunctionalPrinter, NameUtils, ProtobufGenerator}
import scalapb.options.Scalapb

import scala.annotation.tailrec
import scala.collection.mutable
import scala.jdk.CollectionConverters.*

object SwaggerGenerator extends CodeGenApp {

  override def registerExtensions(registry: ExtensionRegistry): Unit = {
    Scalapb.registerAllExtensions(registry)
    AnnotationsProto.registerAllExtensions(registry)
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
            .map(fd => new SwaggerMessagePrinter(fd, implicits))
            .map(_.result)
        )

      case Left(error) => CodeGenResponse.fail(error)
    }
}

private class SwaggerMessagePrinter(fd: FileDescriptor, implicits: DescriptorImplicits) {

  import implicits.*

  private val services = fd.getServices.asScala.toList

  lazy val result: CodeGeneratorResponse.File = {
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setName(s"${getProtoFileName(fd.getName)}.yml")
    b.setContent(content)
    b.build()
  }

  lazy val content: String =
    new FunctionalPrinter()
      .add("---", "swagger: '2.0'", "info:")
      .addIndented(
        "version: 3.1.0",
        s"description: 'REST API generated from ${fd.getName}'",
        s"title: '${fd.getFullName}'"
      )
      .add("tags:")
      .print(services) { case (p, service) => generateTag(service)(p) }
      .add("schemes:")
      .addIndented("- http", "- https")
      .add("consumes:")
      .addIndented("- 'application/json'")
      .add("produces:")
      .addIndented("- 'application/json'")
      .add("paths:")
      .indent
      .print(services) { case (p, service) => generatePaths(service)(p) }
      .outdent
      .add("definitions:")
      .indent
      .call(generateDefinitions(services))
      .outdent
      .result()

  private def generateTag(service: ServiceDescriptor): PrinterEndo = { printer =>
    if (getUnaryCallsWithHttpExtension(service).isEmpty) printer
    else {
      val esd = ExtendedServiceDescriptor(service)
      printer
        .indent
        .add(s"- name: ${esd.name}")
        .add(s"  description: ${esd.comment.map(_.trim).getOrElse(esd.name)}")
        .outdent
    }
  }

  private def generatePaths(service: ServiceDescriptor): PrinterEndo = { printer =>
    if (getUnaryCallsWithHttpExtension(service).isEmpty) printer
    else printer.print(getPaths(service)) { case (p, (path, pathMethods)) => generatePath(path, pathMethods)(p) }
  }

  private def generateDefinitions(services: Seq[ServiceDescriptor]): PrinterEndo = { printer =>
    if (services.forall(getUnaryCallsWithHttpExtension(_).isEmpty)) printer
    else {
      val definitions = services.flatMap(getDefinitions).toSet
      printer.print(definitions) { case (p, definition) => generateDefinition(definition)(p) }
    }
  }

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

  private def getDefinitions(service: ServiceDescriptor) =
    getMethods(service).flatMap(m => extractDefs(m.getInputType) ++ extractDefs(m.getOutputType)).toSet

  private def extractDefs(d: Descriptor) = {
    val explored = mutable.Set.empty[Descriptor]
    def extractDefsRec(d: Descriptor): Set[Descriptor] =
      if (explored.contains(d)) Set()
      else {
        explored.add(d)
        Set(d) ++ d.getFields.asScala.flatMap { f =>
          f.getJavaType match {
            case JavaType.MESSAGE => extractDefsRec(f.getMessageType)
            case _                => Set.empty[Descriptor]
          }
        }
      }
    extractDefsRec(d)
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

  private def generatePath(path: String, pathMethods: Seq[(PatternCase, MethodDescriptor)]): PrinterEndo = { printer =>
    val p1 = printer.add(s"$path:").indent
    pathMethods
      .foldLeft(p1) { case (printer, (patternCase, method)) =>
        printer.call(generateMethod(method, path, patternCase))
      }
      .outdent
  }

  private def generateMethod(m: MethodDescriptor, path: String, patternCase: PatternCase): PrinterEndo = { printer =>
    val pathElements = extractPathElements(path)
    val hasPathVariables = pathElements.nonEmpty
    val http = m.getOptions.getExtension(AnnotationsProto.http)
    patternCase match {
      case PatternCase.GET =>
        printer
          .add("get:")
          .indent
          .call(generateMethodInfo(m))
          .call(generateParameters(m.getInputType, pathElements))
          .outdent
      case PatternCase.POST =>
        printer
          .add("post:")
          .indent
          .call(generateMethodInfo(m))
          .when(hasPathVariables)(_.call(generateParameters(m.getInputType, pathElements, bodyParam = http.getBody)))
          .when(!hasPathVariables)(_.call(generateBodyParameters(m.getInputType)))
          .outdent
      case PatternCase.PUT =>
        printer
          .add("put:")
          .indent
          .call(generateMethodInfo(m))
          .when(hasPathVariables)(_.call(generateParameters(m.getInputType, pathElements, bodyParam = http.getBody)))
          .when(!hasPathVariables)(_.call(generateBodyParameters(m.getInputType)))
          .outdent
      case PatternCase.DELETE =>
        printer
          .add("delete:")
          .indent
          .call(generateMethodInfo(m))
          .call(generateParameters(m.getInputType, pathElements))
          .outdent
      case _ => printer
    }
  }

  private def generateMethodInfo(m: MethodDescriptor): PrinterEndo = {
    val descriptor = ExtendedMethodDescriptor(m)
    val description = descriptor.comment.filterNot(_.isBlank).getOrElse(s"'Generated from ${m.getFullName}'").trim
    _.add("tags:")
      .addIndented(s"- ${m.getService.getName}")
      .add("summary:")
      .addIndented(s"'${m.getName}'")
      .add("description:")
      .addIndented(description)
      .add("produces:")
      .addIndented("['application/json']")
      .add("responses:")
      .indent
      .add("200:")
      .indent
      .add("description: 'Normal response'")
      .add("schema:")
      .addIndented(s"""$$ref: "#/definitions/${m.getOutputType.getName}"""")
      .outdent
      .outdent
  }

  private def generateBodyParameters(inputType: Descriptor): PrinterEndo =
    _.add("parameters:")
      .indent
      .add("- in: 'body'")
      .indent
      .add("name: body")
      .add("schema:")
      .addIndented(s"""$$ref: "#/definitions/${inputType.getName}"""")
      .outdent
      .outdent

  private def generateParameters(
    inputType: Descriptor,
    pathElements: Seq[String],
    prefix: String = "",
    bodyParam: String = ""
  ): PrinterEndo =
    _.when(inputType.getFields.asScala.nonEmpty && prefix.isBlank)(
      _.add("parameters:")
        .print(inputType.getFields.asScala) { case (p, f) =>
          p.call(generateParameter(f, pathElements, prefix, bodyParam))
        }
    )
      .when(inputType.getFields.asScala.nonEmpty && prefix.nonEmpty)(_.print(inputType.getFields.asScala) {
        case (p, f) =>
          p.call(generateParameter(f, pathElements, prefix, bodyParam))
      })

  private def generateParameter(
    field: FieldDescriptor,
    pathElements: Seq[String],
    prefix: String = "",
    bodyParam: String = ""
  ): PrinterEndo = { printer =>
    val fullPathName = NameUtils.snakeCaseToCamelCase(s"$prefix${field.upperScalaName}", upperInitial = true)
    val inPath = pathElements.contains(fullPathName)
    field.getJavaType match {
      case JavaType.MESSAGE if field.getName == bodyParam =>
        printer
          .add("- in: 'body'")
          .indent
          .add("name: body")
          .add("schema:")
          .addIndented(s"""$$ref: "#/definitions/${field.getMessageType.getName}"""")
          .outdent
      case JavaType.MESSAGE =>
        val p = if (prefix.isEmpty) s"${field.getName}." else s"$prefix.${field.getName}."
        printer.call(generateParameters(field.getMessageType, pathElements, p))
      case JavaType.ENUM =>
        printer
          .add(s"- name: $prefix${field.getName}")
          .when(inPath)(_.addIndented("in: path", "required: true", "type: string", "enum:"))
          .when(!inPath)(_.addIndented("in: query", "type: string", "enum:"))
          .addIndented(field.getEnumType.getValues.asScala.toSeq.map(v => s"- ${v.getName}")*)
      case JavaType.INT =>
        printer
          .add(s"- name: $prefix${field.getName}")
          .when(inPath)(_.addIndented("in: path", "required: true", "type: integer", "format: int32"))
          .when(!inPath)(_.addIndented("in: query", "type: integer", "format: int32"))
      case JavaType.LONG =>
        printer
          .add(s"- name: $prefix${field.getName}")
          .when(inPath)(_.addIndented("in: path", "required: true", "type: integer", "format: int64"))
          .when(!inPath)(_.addIndented("in: query", "type: integer", "format: int64"))
      case JavaType.DOUBLE =>
        printer
          .add(s"- name: $prefix${field.getName}")
          .when(inPath)(_.addIndented("in: path", "required: true", "type: number", "format: double"))
          .when(!inPath)(_.addIndented("in: query", "type: number", "format: double"))
      case JavaType.FLOAT =>
        printer
          .add(s"- name: $prefix${field.getName}")
          .when(inPath)(_.addIndented("in: path", "required: true", "type: number", "format: float"))
          .when(!inPath)(_.addIndented("in: query", "type: number", "format: float"))
      case t =>
        printer
          .add(s"- name: $prefix${field.getName}")
          .when(inPath)(_.addIndented("in: path", "required: true", s"type: ${t.name.toLowerCase}"))
          .when(!inPath)(_.addIndented("in: query", s"type: ${t.name.toLowerCase}"))
    }
  }

  private def generateDefinition(d: Descriptor): PrinterEndo = { printer =>
    val fields = d.getFields.asScala
    // no need to generate definition for wrapper type
    if (d.scalaType.fullName.startsWith("com.google.protobuf.wrappers")) printer
    // no need to print map entry
    else if (d.isMapEntry) printer
    else
      printer
        .add(d.getName + ":")
        .indent
        .add("type: object")
        .when(fields.nonEmpty)(
          _.add("properties: ")
            .indent
            .print(fields) { case (printer, field) =>
              if (field.isMapField) {
                val messageType = field.getMessageType
                val mapValue = messageType.getFields.asScala.last
                val mapValueType = mapValue.getJavaType
                printer
                  .add(s"${field.getName}:")
                  .indent
                  .add("type: object", "additionalProperties:")
                  .indent
                  .call(generateMapValueType(mapValue, mapValueType))
                  .outdent
                  .outdent
              } else if (field.isRepeated) {
                printer
                  .add(field.getName + ":")
                  .indent
                  .add("type: array", "items:")
                  .indent
                  .call(generateDefinitionType(field))
                  .outdent
                  .outdent
              } else {
                printer
                  .add(field.getName + ":")
                  .indent
                  .call(generateDefinitionType(field))
                  .outdent
              }
            }
            .outdent
        )
        .outdent
  }

  @tailrec
  private def generateDefinitionType(field: FieldDescriptor): PrinterEndo =
    field.getJavaType match {
      case JavaType.MESSAGE if field.getMessageType.scalaType.fullName.startsWith("com.google.protobuf.wrappers") =>
        generateDefinitionType(field.getMessageType.getFields.asScala.head)
      case JavaType.MESSAGE => _.add(s"""$$ref: "#/definitions/${field.getMessageType.getName}"""")
      case JavaType.ENUM =>
        _.add("type: string", "enum:").add(field.getEnumType.getValues.asScala.toSeq.map(v => s"- ${v.getName}")*)
      case JavaType.INT    => _.add("type: integer", "format: int32")
      case JavaType.LONG   => _.add("type: integer", "format: int64")
      case JavaType.DOUBLE => _.add("type: number", "format: double")
      case JavaType.FLOAT  => _.add("type: number", "format: float")
      case t               => _.add(s"type: ${t.name.toLowerCase}")
    }

  private def generateMapValueType(fd: FieldDescriptor, valueJavaType: JavaType): PrinterEndo = { printer =>
    valueJavaType match {
      case JavaType.INT     => printer.add("type: integer", "format: int32")
      case JavaType.LONG    => printer.add("type: integer", "format: int64")
      case JavaType.FLOAT   => printer.add("type: number", "format: float")
      case JavaType.DOUBLE  => printer.add("type: number", "format: double")
      case JavaType.STRING  => printer.add("type: string")
      case JavaType.MESSAGE => printer.add(s"""$$ref: "#/definitions/${fd.getMessageType.getName}"""")
      case t                => printer.add(s"type: ${t.name.toLowerCase}")
    }
  }
}
