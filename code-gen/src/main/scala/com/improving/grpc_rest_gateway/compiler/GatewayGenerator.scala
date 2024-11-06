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

import scala.jdk.CollectionConverters._

object GatewayGenerator extends CodeGenApp {

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
          } yield new GatewayMessagePrinter(sd, implicits).result
        )

      case Left(error) => CodeGenResponse.fail(error)
    }
}

private class GatewayMessagePrinter(service: ServiceDescriptor, implicits: DescriptorImplicits) {
  import implicits._

  private val extendedFileDescriptor = ExtendedFileDescriptor(service.getFile)
  private val serviceName = service.getName

  private val scalaPackageName = extendedFileDescriptor.scalaPackage.fullName

  private val outputFileName = scalaPackageName.replace('.', '/') + "/" + serviceName + "GatewayHandler.scala"

  lazy val result: CodeGeneratorResponse.File = {
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setName(outputFileName)
    b.setContent(content)
    b.build()
  }

  lazy val content: String =
    new FunctionalPrinter()
      .add(s"package $scalaPackageName")
      .newline
      .add(
        "import _root_.scalapb.GeneratedMessage",
        "import _root_.scalapb.json4s.JsonFormat",
        "import _root_.com.improving.grpc_rest_gateway.runtime.handlers._",
        "import _root_.io.grpc._",
        "import _root_.io.netty.handler.codec.http.{HttpMethod, QueryStringDecoder}"
      )
      .newline
      .add(
        "import scala.concurrent.{ExecutionContext, Future}",
        "import scalapb.json4s.JsonFormatException",
        "import scala.util._"
      )
      .newline
      .print(Seq(service)) { case (p, s) => generateService(s)(p) }
      .result()

  private def generateService(service: ServiceDescriptor): PrinterEndo = { printer =>
    val descriptor = ExtendedServiceDescriptor(service)

    // this is NOT the FQN of the service, we are generating gateway handler in the same package as GRPC service
    val grpcService = descriptor.companionObject.name
    printer
      .add(s"class ${service.getName}GatewayHandler(channel: ManagedChannel)(implicit ec: ExecutionContext)")
      .indent
      .add(
        "extends GrpcGatewayHandler(channel)(ec) {",
        s"""override val name: String = "${service.getName}"""",
        s"private val stub = $grpcService.stub(channel)"
      )
      .call(generateHttpMethodToUrisMap(service))
      .newline
      .call(generateDispatchCall(service))
      .outdent
      .add("}")
      .newline
  }

  private def getUnaryCallsWithHttpExtension(service: ServiceDescriptor) =
    service.getMethods.asScala.filter { m =>
      // only unary calls with http method specified
      !m.isClientStreaming && !m.isServerStreaming && m.getOptions.hasExtension(AnnotationsProto.http)
    }

  private def generateDispatchCall(service: ServiceDescriptor): PrinterEndo = { printer =>
    val methods = getUnaryCallsWithHttpExtension(service)

    printer
      .add(
        s"override protected def dispatchCall(method: HttpMethod, uri: String, body: String): Future[GeneratedMessage] = {"
      )
      .indent
      .add(
        "val queryString = new QueryStringDecoder(uri)",
        "val path = queryString.path",
        "val methodName = method.name"
      )
      .call(generateMethodHandlers(methods))
      .outdent
      .add("}")
  }

  private def generateMethodHandlers(methods: Seq[MethodDescriptor]): PrinterEndo = { printer =>
    var started = false

    val p =
      methods.foldLeft(printer) { case (p, method) =>
        val methodDescriptor = ExtendedMethodDescriptor(method)
        // FQN of the input type, this done on the assumption that input types can be in the different package
        val fullInputName = methodDescriptor.inputType.scalaType
        val methodName = method.getName.charAt(0).toLower + method.getName.substring(1)
        val http = method.getOptions.getExtension(AnnotationsProto.http)
        http.getPatternCase match {
          case PatternCase.GET =>
            val p1 =
              if (started) p.add(s"""} else if (isSupportedCall("GET", "${http.getGet}", methodName, path)) {""")
              else {
                started = true
                p.add(s"""if (isSupportedCall("GET", "${http.getGet}", methodName, path)) {""")
              }
            p1.indent
              .add(s"""val parameters = mergeParameters("${http.getGet}", queryString)""")
              .add("val input = Try {")
              .indent
              .call(generateInputFromQueryString(method.getInputType, fullInputName))
              .outdent
              .add("}")
              .add(s"Future.fromTry(input).flatMap(stub.$methodName)")
              .outdent

          case PatternCase.PUT =>
            val p1 =
              if (started) p.add(s"""} else if (isSupportedCall("PUT", "${http.getPut}", methodName, path)) {""")
              else {
                started = true
                p.add(s"""if (isSupportedCall("PUT", "${http.getPut}", methodName, path)) {""")
              }
            p1.indent
              .add(s"""val parameters = mergeParameters("${http.getPut}", queryString)""")
              .call(generateInputFromQueryString(method.getInputType, fullInputName))
              .outdent

          case PatternCase.POST =>
            val p1 =
              if (started) p.add(s"""} else if (isSupportedCall("POST", "${http.getPost}", methodName, path)) {""")
              else {
                started = true
                p.add(s"""if (isSupportedCall("POST", "${http.getPost}", methodName, path)) {""")
              }
            p1.indent
              .add("for {")
              .addIndented(
                s"""msg <- Future.fromTry(Try(JsonFormat.fromJsonString[$fullInputName](body)).recoverWith(jsonException2GatewayExceptionPF))""",
                s"res <- stub.$methodName(msg)"
              )
              .add("} yield res")
              .outdent

          case PatternCase.DELETE =>
            val p1 =
              if (started) p.add(s"""} else if (isSupportedCall("DELETE", "${http.getDelete}", methodName, path)) {""")
              else {
                started = true
                p.add(s"""if (isSupportedCall("DELETE", "${http.getDelete}", methodName, path)) {""")
              }
            p1.indent
              .add(s"""val parameters = mergeParameters("${http.getDelete}", queryString)""")
              .call(generateInputFromQueryString(method.getInputType, fullInputName))
              .outdent

          case PatternCase.PATCH =>
            val p1 =
              if (started) p.add(s"""} else if (isSupportedCall("PATCH", "${http.getPatch}", methodName, path)) {""")
              else {
                started = true
                p.add(s"""if (isSupportedCall("PATCH", "${http.getPatch}", methodName, path)) {""")
              }
            p1.indent
              .add(s"""val parameters = mergeParameters("${http.getPatch}", queryString)""")
              .call(generateInputFromQueryString(method.getInputType, fullInputName))
              .outdent

          case _ => p
        }
      }

    if (started) p.add(s"""} else Future.failed(InvalidArgument(s"No route defined for $$methodName($$path)"))""")
    else p
  }

  private def generateInputFromQueryString(d: Descriptor, fullName: String, prefix: String = ""): PrinterEndo = {
    printer =>
      val args = d.getFields.asScala.map(f => s"${f.getJsonName} = ${f.getJsonName}").mkString(", ")

      // TODO: test each case with field name defined as Protobuf format "my_field" and Java format "myField"
      // If field name in Protobuf is defined with underscore then inputName and jsonName will be different
      printer
        .print(d.getFields.asScala) { case (p, f) =>
          val inputName = getInputName(f, prefix)
          val jsonName = f.getJsonName
          f.getJavaType match {
            case JavaType.MESSAGE =>
              p.add(s"val $jsonName = {")
                .indent
                .call(
                  generateInputFromQueryString(
                    f.getMessageType,
                    ExtendedMessageDescriptor(f.getMessageType).scalaType.fullName,
                    s"$prefix.$inputName"
                  )
                )
                .outdent
                .add("}")
            case JavaType.ENUM =>
              p.add(s"""val $jsonName = ${f.getName}.valueOf(parameters.getOrElse("$prefix$inputName", ""))""")
            case JavaType.BOOLEAN =>
              p.add(s"""val $jsonName = parameters.getOrElse("$prefix$inputName", "").toBoolean""")
            case JavaType.DOUBLE => p.add(s"""val $jsonName = parameters.getOrElse("$prefix$inputName", "").toDouble""")
            case JavaType.FLOAT  => p.add(s"""val $jsonName = parameters.getOrElse("$prefix$inputName", "").toFloat""")
            case JavaType.INT    => p.add(s"""val $jsonName = parameters.getOrElse("$prefix$inputName", "").toInt""")
            case JavaType.LONG   => p.add(s"""val $jsonName = parameters.getOrElse("$prefix$inputName", "").toLong""")
            case JavaType.STRING => p.add(s"""val $jsonName = parameters.getOrElse("$prefix$inputName", "")""")
            case jt              => throw new Exception(s"Unknown java type: $jt")
          }
        }
        .add(s"$fullName($args)")
  }

  private def getInputName(d: FieldDescriptor, prefix: String = ""): String = {
    val name = prefix.split(".").filter(_.nonEmpty).map(s => s.charAt(0).toUpper + s.substring(1)).mkString + d.getName
    name.charAt(0).toLower + name.substring(1)
  }

  private def generateHttpMethodToUrisMap(service: ServiceDescriptor): PrinterEndo = { printer =>
    val methods = getUnaryCallsWithHttpExtension(service)

    val httpMethodsToUrisMap =
      methods.foldLeft(Map.empty[String, Seq[String]]) { case (result, method) =>
        val http = method.getOptions.getExtension(AnnotationsProto.http)
        http.getPatternCase match {
          case PatternCase.GET =>
            val updatedValues =
              result.get(PatternCase.GET.name()) match {
                case Some(values) => values :+ http.getGet
                case None         => Seq(http.getGet)
              }
            result + (PatternCase.GET.name() -> updatedValues)

          case PatternCase.PUT =>
            val updatedValues =
              result.get(PatternCase.PUT.name()) match {
                case Some(values) => values :+ http.getGet
                case None         => Seq(http.getPut)
              }
            result + (PatternCase.PUT.name() -> updatedValues)

          case PatternCase.POST =>
            val updatedValues =
              result.get(PatternCase.POST.name()) match {
                case Some(values) => values :+ http.getGet
                case None         => Seq(http.getPost)
              }
            result + (PatternCase.POST.name() -> updatedValues)

          case PatternCase.DELETE =>
            val updatedValues =
              result.get(PatternCase.DELETE.name()) match {
                case Some(values) => values :+ http.getGet
                case None         => Seq(http.getDelete)
              }
            result + (PatternCase.DELETE.name() -> updatedValues)

          case PatternCase.PATCH =>
            val updatedValues =
              result.get(PatternCase.PATCH.name()) match {
                case Some(values) => values :+ http.getGet
                case None         => Seq(http.getPatch)
              }
            result + (PatternCase.PATCH.name() -> updatedValues)
          case _ => result
        }
      }

    // generate key value pair for the map
    val mapKeys =
      httpMethodsToUrisMap.zipWithIndex.foldLeft(Seq.empty[String]) { case (result, ((methodName, uris), index)) =>
        val urisSeq = s"""Seq(${uris.map(uri => " \n" + "  \"" + uri + "\"").mkString(", ")}\n)"""
        // each element of seq is separated by "," except for last element
        val keys = if (index == httpMethodsToUrisMap.size - 1) urisSeq else s"""$urisSeq,"""
        result :+ s""""$methodName" -> $keys"""
      }

    printer
      .add("override protected val httpMethodsToUrisMap: Map[String, Seq[String]] = Map(")
      .indent
      .seq(mapKeys)
      .outdent
      .add(")")
  }
}
