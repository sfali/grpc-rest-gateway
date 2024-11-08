package com.improving.grpc_rest_gateway.compiler

import com.google.api.{AnnotationsProto, HttpRule}
import com.google.api.HttpRule.PatternCase
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType
import com.google.protobuf.Descriptors.{Descriptor, FieldDescriptor, MethodDescriptor, ServiceDescriptor}
import com.google.protobuf.ExtensionRegistry
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import protocgen.{CodeGenApp, CodeGenRequest, CodeGenResponse}
import scalapb.compiler.FunctionalPrinter.PrinterEndo
import scalapb.compiler.{DescriptorImplicits, FunctionalPrinter, NameUtils, ProtobufGenerator}
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

  private var ifStatementStarted = false
  private var pathsToConstantMap: Map[(PatternCase, String), String] = Map.empty
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
        "import scalapb.GeneratedMessage",
        "import scalapb.json4s.JsonFormat",
        "import com.improving.grpc_rest_gateway.runtime.handlers._",
        "import io.grpc._",
        "import io.netty.handler.codec.http.{HttpMethod, QueryStringDecoder}"
      )
      .newline
      .add(
        "import scala.concurrent.{ExecutionContext, Future}",
        "import scalapb.json4s.JsonFormatException",
        "import scala.util._"
      )
      .newline
      .call(generateCompanionObject(service))
      .newline
      .print(Seq(service)) { case (p, s) => generateService(s)(p) }
      .result()

  private def generateCompanionObject(service: ServiceDescriptor): PrinterEndo = { printer =>
    val paths =
      service.getMethods.asScala.filter(_.getOptions.hasExtension(AnnotationsProto.http)).flatMap { method =>
        val uppercaseMethodName = NameUtils.snakeCaseToCamelCase(method.getName, upperInitial = true)
        val paths = extractPaths(method)
        if (paths.size == 1) {
          val (patternCase, path) = paths.head
          val constantName =
            s"${NameUtils.snakeCaseToCamelCase(patternCase.name().toLowerCase, upperInitial = true)}${uppercaseMethodName}Path"
          pathsToConstantMap = pathsToConstantMap + ((patternCase, path) -> constantName)
          Seq(s"""private val $constantName = "$path"""")
        } else
          paths.zipWithIndex.map { case ((patternCase, path), index) =>
            val constantName =
              s"${NameUtils.snakeCaseToCamelCase(patternCase.name().toLowerCase, upperInitial = true)}${uppercaseMethodName}Path${index + 1}"
            pathsToConstantMap = pathsToConstantMap + ((patternCase, path) -> constantName)
            s"""private val $constantName = "$path""""
          }
      }
    printer.add(s"object ${service.getName}GatewayHandler {").indent.seq(paths).outdent.add("}")
  }

  private def generateService(service: ServiceDescriptor): PrinterEndo = { printer =>
    val descriptor = ExtendedServiceDescriptor(service)
    // this is NOT the FQN of the service, we are generating gateway handler in the same package as GRPC service
    val grpcService = descriptor.companionObject.name
    val implName = s"${service.getName}GatewayHandler"
    printer
      .add(s"class $implName(channel: ManagedChannel)(implicit ec: ExecutionContext)")
      .indent
      .add(
        "extends GrpcGatewayHandler(channel)(ec) {",
        s"import $implName._",
        s"""override val name: String = "${service.getName}"""",
        s"private val stub = $grpcService.stub(channel)"
      )
      .call(generateHttpMethodToUrisMap)
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
    val p =
      methods.foldLeft(printer) { case (printer, method) =>
        val methodDescriptor = ExtendedMethodDescriptor(method)
        // FQN of the input type, this done on the assumption that input types can be in the different package
        val fullInputName = methodDescriptor.inputType.scalaType
        val methodName = method.getName.charAt(0).toLower + method.getName.substring(1)
        val http = method.getOptions.getExtension(AnnotationsProto.http)
        val bindings = http +: http.getAdditionalBindingsList.asScala
        bindings.foldLeft(printer) { case (printer, httpRule) =>
          generateMethodHandler(method, httpRule, methodName, fullInputName)(printer)
        }
      }

    if (ifStatementStarted)
      p.add(s"""} else Future.failed(InvalidArgument(s"No route defined for $$methodName($$path)"))""")
    else p
  }

  private def generateMethodHandler(
    method: MethodDescriptor,
    http: HttpRule,
    methodName: String,
    fullInputName: String
  ): PrinterEndo = { p =>
    http.getPatternCase match {
      case PatternCase.GET =>
        val constantName = pathsToConstantMap((PatternCase.GET, http.getGet))
        val p1 =
          if (ifStatementStarted)
            p.add(s"""} else if (isSupportedCall(HttpMethod.GET.name, $constantName, methodName, path)) {""")
          else {
            ifStatementStarted = true
            p.add(s"""if (isSupportedCall(HttpMethod.GET.name, $constantName, methodName, path)) {""")
          }
        p1.indent
          .add(s"""val parameters = mergeParameters($constantName, queryString)""")
          .add("val input = Try {")
          .indent
          .call(generateInputFromQueryString(method.getInputType, fullInputName, required = true))
          .outdent
          .add("}")
          .add(s"Future.fromTry(input).flatMap(stub.$methodName)")
          .outdent

      case PatternCase.PUT =>
        val constantName = pathsToConstantMap((PatternCase.PUT, http.getPut))
        val p1 =
          if (ifStatementStarted)
            p.add(s"""} else if (isSupportedCall(HttpMethod.PUT.name, $constantName, methodName, path)) {""")
          else {
            ifStatementStarted = true
            p.add(s"""if (isSupportedCall(HttpMethod.PUT.name, $constantName, methodName, path)) {""")
          }

        p1.call(
          generateBodyParam(
            method = method,
            fullInputName = fullInputName,
            methodName = methodName,
            body = http.getBody,
            path = http.getPost,
            constantName = constantName
          )
        )

      case PatternCase.POST =>
        val constantName = pathsToConstantMap((PatternCase.POST, http.getPost))

        val p1 =
          if (ifStatementStarted)
            p.add(s"""} else if (isSupportedCall(HttpMethod.POST.name, $constantName, methodName, path)) {""")
          else {
            ifStatementStarted = true
            p.add(s"""if (isSupportedCall(HttpMethod.POST.name, $constantName, methodName, path)) {""")
          }
        p1.call(
          generateBodyParam(
            method = method,
            fullInputName = fullInputName,
            methodName = methodName,
            body = http.getBody,
            path = http.getPost,
            constantName = constantName
          )
        )

      case PatternCase.DELETE =>
        val constantName = pathsToConstantMap((PatternCase.DELETE, http.getDelete))
        val p1 =
          if (ifStatementStarted)
            p.add(s"""} else if (isSupportedCall(HttpMethod.DELETE.name, $constantName, methodName, path)) {""")
          else {
            ifStatementStarted = true
            p.add(s"""if (isSupportedCall(HttpMethod.DELETE.name, $constantName", methodName, path)) {""")
          }
        p1.indent
          .add(s"""val parameters = mergeParameters($constantName", queryString)""")
          .call(generateInputFromQueryString(method.getInputType, fullInputName, required = true))
          .outdent

      case PatternCase.PATCH =>
        val constantName = pathsToConstantMap((PatternCase.PATCH, http.getPatch))
        val p1 =
          if (ifStatementStarted)
            p.add(s"""} else if (isSupportedCall(HttpMethod.PATCH.name, $constantName, methodName, path)) {""")
          else {
            ifStatementStarted = true
            p.add(s"""if (isSupportedCall(HttpMethod.PATCH.name, $constantName, methodName, path)) {""")
          }
        p1.indent
          .add(s"""val parameters = mergeParameters($constantName, queryString)""")
          .call(generateInputFromQueryString(method.getInputType, fullInputName, required = true))
          .outdent

      case _ => p
    }
  }

  private def generateBodyParam(
    method: MethodDescriptor,
    fullInputName: String,
    methodName: String,
    body: String,
    path: String,
    constantName: String
  ): PrinterEndo = { printer =>
    if (body.nonEmpty) {
      if (body == "*") {
        printer
          .indent
          .add(
            s"val input = Try(JsonFormat.fromJsonString[$fullInputName](body))"
          )
          .addIndented(".recoverWith(jsonException2GatewayExceptionPF)")
          .add("for {")
          .addIndented(
            s"""msg <- Future.fromTry(input)""",
            s"res <- stub.$methodName(msg)"
          )
          .add("} yield res")
          .outdent
      } else {
        val inputTypeDescriptor = method.getInputType
        val maybeDescriptor = inputTypeDescriptor.getFields.asScala.find(_.getName == body)
        maybeDescriptor match {
          case Some(descriptor) =>
            val fd = ExtendedFieldDescriptor(descriptor)
            val bodyFullType = fd.singleScalaTypeName
            val optional = !fd.noBox // this is an Option in parent type
            val args =
              inputTypeDescriptor.getFields.asScala.map(f => s"${f.getJsonName} = ${f.getJsonName}").mkString(", ")
            printer
              .indent
              .add("val parsedBody = ")
              .when(optional)(
                _.addIndented(s"Try(Option(JsonFormat.fromJsonString[$bodyFullType](body)))")
              )
              .when(!optional)(_.addIndented(s"Try(JsonFormat.fromJsonString[$bodyFullType](body))"))
              .addIndented(".recoverWith(jsonException2GatewayExceptionPF)")
              .add(s"""val parameters = mergeParameters($constantName, queryString)""")
              .add("val input = Try {")
              .indent
              .call(generateInputFromQueryStringSingle(inputTypeDescriptor, required = true, ignoreFieldName = body))
              .add(s"""val $body = parsedBody.get""")
              .add(s"$fullInputName($args)")
              .outdent
              .add("}")
              .add("for {")
              .addIndented(
                s"""msg <- Future.fromTry(input)""",
                s"res <- stub.$methodName(msg)"
              )
              .add("} yield res")
              .outdent
          case None =>
            throw new RuntimeException(
              s"Unable to determine body type for input: $fullInputName, path: $path,body: $body, method: $methodName"
            )
        }

      }
    } else
      throw new RuntimeException(
        s"Body parameter is empty for input: $fullInputName, path: $path, method: $methodName"
      )
  }

  /** Generates inputs.
    *
    * @param d
    *   current descriptor
    * @param fullName
    *   full name of parent type
    * @param required
    *   flag to indicate if current is type is required, this flag will dictate how primitive types will be evaluated
    * @param prefix
    *   prefix (if applicable) of the query string
    * @return
    *   FunctionalPrinter function
    */
  private def generateInputFromQueryString(
    d: Descriptor,
    fullName: String,
    required: Boolean,
    prefix: String = ""
  ): PrinterEndo = { printer =>
    val args = d.getFields.asScala.map(f => s"${f.getJsonName} = ${f.getJsonName}").mkString(", ")

    // If field name in Protobuf is defined with underscore then inputName and jsonName will be different
    printer
      .call(generateInputFromQueryStringSingle(d, required, prefix))
      .add(s"$fullName($args)")
  }

  private def generateInputFromQueryStringSingle(
    d: Descriptor,
    required: Boolean,
    prefix: String = "",
    ignoreFieldName: String = ""
  ): PrinterEndo =
    // If field name in Protobuf is defined with underscore then inputName and jsonName will be different
    _.print(d.getFields.asScala) { case (p, f) =>
      val inputName = getInputName(f, prefix)
      val jsonName = f.getJsonName
      f.getJavaType match {
        case JavaType.MESSAGE if ignoreFieldName == inputName => p
        case JavaType.MESSAGE =>
          val required = f.noBox
          val optional = !required
          p.when(required)(_.add(s"""val $jsonName = {"""))
            .when(optional)(_.add(s"""val $jsonName = Try {"""))
            .indent
            .call(
              generateInputFromQueryString(
                d = f.getMessageType,
                fullName = f.singleScalaTypeName,
                required = required,
                prefix = if (prefix.isBlank) s"$inputName." else s"$prefix.$inputName."
              )
            )
            .outdent
            .when(required)(_.add("}"))
            .when(optional)(_.add("}.toOption"))
        case JavaType.ENUM =>
          p.add(s"""val $jsonName = ${f.getName}.valueOf(parameters.getOrElse("$prefix$inputName", ""))""")
        case JavaType.BOOLEAN =>
          p.add(s"""val $jsonName = parameters.getOrElse("$prefix$inputName", "").toBoolean""")
        case JavaType.DOUBLE =>
          p.when(required)(_.add(s"""val $jsonName = parameters.toDouble("$prefix$inputName")"""))
            .when(!required)(_.add(s"""val $jsonName = parameters.toDouble("$prefix$inputName", "")"""))
        case JavaType.FLOAT =>
          p.when(required)(_.add(s"""val $jsonName = parameters.toFloat("$prefix$inputName")"""))
            .when(!required)(_.add(s"""val $jsonName = parameters.toFloat("$prefix$inputName", "")"""))
        case JavaType.INT =>
          p.when(required)(_.add(s"""val $jsonName = parameters.toInt("$prefix$inputName")"""))
            .when(!required)(_.add(s"""val $jsonName = parameters.toInt("$prefix$inputName", "")"""))
        case JavaType.LONG =>
          p.when(required)(_.add(s"""val $jsonName = parameters.toLong("$prefix$inputName")"""))
            .when(!required)(_.add(s"""val $jsonName = parameters.toLong("$prefix$inputName", "")"""))
        case JavaType.STRING => p.add(s"""val $jsonName = parameters.toStringValue("$prefix$inputName")""")
        case jt              => throw new Exception(s"Unknown java type: $jt")
      }
    }

  private def getInputName(d: FieldDescriptor, prefix: String = ""): String = {
    val name = prefix.split(".").filter(_.nonEmpty).map(s => s.charAt(0).toUpper + s.substring(1)).mkString + d.getName
    name.charAt(0).toLower + name.substring(1)
  }

  private def generateHttpMethodToUrisMap: PrinterEndo = { printer =>
    val httpMethodsToUrisMap =
      pathsToConstantMap.foldLeft(Map.empty[PatternCase, Seq[String]]) { case (result, ((patternCase, path), value)) =>
        val updatedValues =
          result.get(patternCase) match {
            case Some(values) => values :+ value
            case None         => Seq(value)
          }
        result + (patternCase -> updatedValues)
      }

    // generate key value pair for the map
    val mapKeys =
      httpMethodsToUrisMap.zipWithIndex.foldLeft(Seq.empty[String]) { case (result, ((methodName, cons), index)) =>
        val pathConstantsSeq = s"""Seq(${cons.sorted.map(uri => " \n" + "  " + uri).mkString(", ")}\n)"""
        // each element of seq is separated by "," except for last element
        val keys = if (index == httpMethodsToUrisMap.size - 1) pathConstantsSeq else s"""$pathConstantsSeq,"""
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
