package com.improving.grpc_rest_gateway.compiler

import com.google.api.AnnotationsProto
import com.google.api.HttpRule.PatternCase
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType
import com.google.protobuf.Descriptors.{Descriptor, FieldDescriptor, MethodDescriptor, ServiceDescriptor}
import com.google.protobuf.ExtensionRegistry
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import protocgen.{CodeGenApp, CodeGenRequest, CodeGenResponse}
import scalapb.compiler.FunctionalPrinter.PrinterEndo
import scalapb.compiler.{DescriptorImplicits, FunctionalPrinter, ProtobufGenerator}
import scalapb.options.Scalapb

import scala.collection.mutable
import scala.jdk.CollectionConverters._

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
          for {
            file <- request.filesToGenerate
            sd <- file.getServices.asScala
          } yield new SwaggerMessagePrinter(sd, implicits).result
        )

      case Left(error) => CodeGenResponse.fail(error)
    }
}

private class SwaggerMessagePrinter(service: ServiceDescriptor, implicits: DescriptorImplicits) {

  import implicits._

  private val extendedFileDescriptor = ExtendedFileDescriptor(service.getFile)
  private val serviceName = service.getName

  private val outputFileName = serviceName + ".yml"

  lazy val result: CodeGeneratorResponse.File = {
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setName(outputFileName)
    b.setContent(content)
    b.build()
  }

  private val extendedServiceDescriptor = ExtendedServiceDescriptor(service)

  lazy val content: String = {
    val methods =
      extendedServiceDescriptor.methods.filter { m =>
        val options = m.toProto.getOptions
        // only unary calls with http method specified
        !m.isClientStreaming && !m.isServerStreaming && options.hasExtension(AnnotationsProto.http)
      }

    val paths = methods.groupBy(extractPath)
    val definitions = methods.flatMap(m => extractDefs(m.getInputType) ++ extractDefs(m.getOutputType)).toSet

    println(s"METHODS: ${methods.map(_.getName).mkString("[", ", ", "]")}")
    println(s"PATHS: ${paths.keys.mkString("[", ", ", "]")}")
    println(s"DEFINITIONS: ${definitions.map(_.getName).mkString("[", ", ", "]")}")

    new FunctionalPrinter()
      .add("swagger: '2.0'", "info:")
      .addIndented(
        s"version: ${BuildInfo.version}",
        s"title: '${extendedFileDescriptor.fileDescriptorObject.fullName}'",
        s"description: 'REST API generated from $serviceName'"
      )
      .add("schemes:")
      .addIndented("- http", "- https")
      .add("consumes:")
      .addIndented("- 'application/json'")
      .add("produces:")
      .addIndented("- 'application/json'")
      .add("paths:")
      .indent
      .print(paths) { case (p, m) => generatePath(m)(p) }
      .outdent
      .add("definitions:")
      .indent
      .print(definitions) { case (p, d) => generateDefinition(d)(p) }
      .outdent
      .result()
  }

  private def extractPath(m: MethodDescriptor): String = {
    val http = m.getOptions.getExtension(AnnotationsProto.http)
    http.getPatternCase match {
      case PatternCase.GET    => http.getGet
      case PatternCase.POST   => http.getPost
      case PatternCase.PUT    => http.getPut
      case PatternCase.DELETE => http.getDelete
      case PatternCase.PATCH  => http.getPatch
      case _                  => ""
    }
  }

  private def extractDefs(d: Descriptor): Set[Descriptor] = {
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

  private def generatePath(pathMethods: (String, Seq[MethodDescriptor])): PrinterEndo = { printer =>
    pathMethods match {
      case (path, methods) =>
        printer
          .add(s"$path:")
          .indent
          .print(methods) { case (p, m) => generateMethod(m)(p) }
          .outdent
    }
  }

  private def generateMethod(m: MethodDescriptor): PrinterEndo = { printer =>
    val http = m.getOptions.getExtension(AnnotationsProto.http)
    http.getPatternCase match {
      case PatternCase.GET =>
        printer
          .add("get:")
          .indent
          .call(generateMethodInfo(m))
          .call(generateQueryParameters(m.getInputType))
          .outdent
      case PatternCase.POST =>
        printer
          .add("post:")
          .indent
          .call(generateMethodInfo(m))
          .call(generateBodyParameters(m.getInputType))
          .outdent
      case PatternCase.PUT =>
        printer
          .add("put:")
          .indent
          .call(generateMethodInfo(m))
          .call(generateBodyParameters(m.getInputType))
          .outdent
      case PatternCase.DELETE =>
        printer
          .add("delete:")
          .indent
          .call(generateMethodInfo(m))
          .call(generateQueryParameters(m.getInputType))
          .outdent
      case _ => printer
    }
  }

  private def generateMethodInfo(m: MethodDescriptor): PrinterEndo =
    _.add("tags:")
      .addIndented(s"- ${m.getService.getName}")
      .add("summary:")
      .addIndented(s"'${m.getName}'")
      .add("description:")
      .addIndented(s"'Generated from ${m.getFullName}'")
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

  private def generateQueryParameters(inputType: Descriptor, prefix: String = ""): PrinterEndo =
    _.when(inputType.getFields.asScala.nonEmpty)(
      _.add("parameters:")
        .print(inputType.getFields.asScala) { case (p, f) =>
          p.call(generateQueryParameter(f))
        }
    )

  private def generateQueryParameter(field: FieldDescriptor, prefix: String = ""): PrinterEndo = { printer =>
    field.getJavaType match {
      case JavaType.MESSAGE =>
        printer.call(
          generateQueryParameters(field.getMessageType, s"$prefix.${field.getJsonName}")
        )
      case JavaType.ENUM =>
        printer
          .add(s"- name: $prefix${field.getJsonName}")
          .addIndented("in: query", s"type: string", "enum:")
          .addIndented(field.getEnumType.getValues.asScala.toSeq.map(v => s"- ${v.getName}"): _*)
      case JavaType.INT =>
        printer
          .add(s"- name: $prefix${field.getJsonName}")
          .addIndented("in: query", "type: integer", "format: int32")
      case JavaType.LONG =>
        printer
          .add(s"- name: $prefix${field.getJsonName}")
          .addIndented("in: query", "type: integer", "format: int64")
      case t =>
        printer
          .add(s"- name: $prefix${field.getJsonName}")
          .addIndented("in: query", s"type: ${t.name.toLowerCase}")
    }
  }

  private def generateDefinition(d: Descriptor): PrinterEndo =
    _.add(d.getName + ":")
      .indent
      .add("type: object")
      .when(d.getFields.asScala.nonEmpty)(
        _.add("properties: ")
          .indent
          .print(d.getFields.asScala) { case (printer, field) =>
            if (field.isRepeated) {
              printer
                .add(field.getJsonName + ":")
                .indent
                .add("type: array", "items:")
                .indent
                .call(generateDefinitionType(field))
                .outdent
                .outdent
            } else {
              printer
                .add(field.getJsonName + ":")
                .indent
                .call(generateDefinitionType(field))
                .outdent
            }
          }
          .outdent
      )
      .outdent

  private def generateDefinitionType(field: FieldDescriptor): PrinterEndo =
    field.getJavaType match {
      case JavaType.MESSAGE => _.add(s"""$$ref: "#/definitions/${field.getMessageType.getName}"""")
      case JavaType.ENUM =>
        _.add("type: string", "enum:").add(
          field.getEnumType.getValues.asScala.toSeq.map(v => s"- ${v.getName}"): _*
        )
      case JavaType.INT  => _.add("type: integer", "format: int32")
      case JavaType.LONG => _.add("type: integer", "format: int64")
      case t             => _.add(s"type: ${t.name.toLowerCase}")
    }
}
