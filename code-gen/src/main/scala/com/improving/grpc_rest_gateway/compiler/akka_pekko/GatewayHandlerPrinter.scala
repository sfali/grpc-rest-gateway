package com.improving
package grpc_rest_gateway
package compiler
package akka_pekko

import com.google.api.HttpRule.PatternCase
import com.google.protobuf.Descriptors.{MethodDescriptor, ServiceDescriptor}
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import compiler.utils.{Formatter, GenerateDelegateFunctions, GenerateImportStatements}
import scalapb.compiler.FunctionalPrinter.PrinterEndo
import scalapb.compiler.{DescriptorImplicits, FunctionalPrinter}

import scala.collection.mutable

class GatewayHandlerPrinter(packageNamePrefix: String, service: ServiceDescriptor, implicits: DescriptorImplicits) {
  import implicits.*

  private val extendedFileDescriptor = ExtendedFileDescriptor(service.getFile)
  private val serviceName = service.getName
  private val specificationName = getProtoFileName(extendedFileDescriptor.file.getName)
  private val scalaPackageName = extendedFileDescriptor.scalaPackage.fullName
  private val handlerClassName = serviceName + "GatewayHandler"
  private val clientClasName = s"${serviceName}Client"
  private val outputFileName = scalaPackageName.replace('.', '/') + "/" + handlerClassName + ".scala"
  private val wildcardImport = extendedFileDescriptor.V.WildcardImport
  private val methods = getUnaryCallsWithHttpExtension(service).toList
  private var routeStarted = false
  private val pathVariableToJavaType = mutable.Map.empty[String, Map[String, String]]
  private val javaTypeToPathMatcherMap =
    Map("INT" -> "IntNumber", "LONG" -> "LongNumber", "DOUBLE" -> "DoubleNumber").withDefaultValue("Segment")

  lazy val result: CodeGeneratorResponse.File = {
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setName(outputFileName)
    b.setContent(Formatter.format(content))
    b.build()
  }

  private lazy val content =
    new FunctionalPrinter()
      .add("/*", " * Generated by GRPC-REST gateway compiler. DO NOT EDIT.", " */")
      .add(s"package $scalaPackageName")
      .newline
      .add(
        s"import com.improving.grpc_rest_gateway.runtime",
        s"import runtime.core.$wildcardImport",
        s"import runtime.handlers.GrpcGatewayHandler"
      )
      .call(GenerateImportStatements(scalaPackageName, implicits, methods))
      .newline
      .when(packageNamePrefix == "pekko")(_.add("import org.apache.pekko"))
      .add(
        s"import $packageNamePrefix.grpc.GrpcClientSettings",
        s"import $packageNamePrefix.actor.ClassicActorSystemProvider",
        s"import $packageNamePrefix.http.scaladsl.server.Route",
        s"import $packageNamePrefix.http.scaladsl.server.Directives.$wildcardImport"
      )
      .newline
      .add(
        "import scala.concurrent.ExecutionContext",
        "import scala.util.Try"
      )
      .newline
      .call(generateService)
      .newline
      .call(generateCompanionObject)
      .result()

  private def generateService: PrinterEndo =
    _.add(s"class $handlerClassName(settings: GrpcClientSettings)(implicit sys: ClassicActorSystemProvider)")
      .indent
      .add("extends GrpcGatewayHandler {")
      .outdent
      .newline
      .indent
      .add("private implicit val ec: ExecutionContext = sys.classicSystem.dispatcher")
      .add(s"private val client = $clientClasName(settings)")
      .add(s"""override val specificationName: String = "$specificationName"""")
      .newline
      .call(generateRoutDefinitions)
      .outdent
      .newline
      .call(GenerateDelegateFunctions(implicits, "completeResponse", methods))
      .add("}")

  private def generateRoutDefinitions: PrinterEndo = { printer =>
    printer
      .add("override val route: Route = ")
      .indent(2)
      .print(methods)((p, method) => generateRouteForMethod(method)(p))
      .outdent
      .add("}}")
      .outdent(1)
  }

  private def generateRouteForMethod(method: MethodDescriptor): PrinterEndo = { printer =>
    extractPaths(method).foldLeft(printer) { case (printer, (patternCase, path, body)) =>
      printer.call(generateRoute(method, patternCase, path, body))
    }
  }

  private def generateRoute(method: MethodDescriptor, patternCase: PatternCase, path: String, body: String): PrinterEndo = {
    printer =>
      val prefix =
        if (routeStarted) "} ~ "
        else {
          routeStarted = true
          "handleExceptions(exceptionHandler) {"
        }

      if (isMethodAllowed(patternCase)) {
        val parsedPath = parsePath(method, path)
        printer
          .when(prefix.nonEmpty)(_.outdent)
          .add(s"$prefix$parsedPath")
          .indent
          .add(s"${patternCase.name().toLowerCase} {")
          .call(generateParametersIfApplicable(path, body, method.getName))
          .add("}")
      } else printer
  }

  private def generateParametersIfApplicable(
    fullPath: String,
    body: String,
    methodName: String
  ): PrinterEndo = { printer =>
    val hasParameters = body.isBlank || body != "*"
    if (hasParameters) {
      printer
        .indent
        .add("parameterMultiMap { queryParameters =>")
        .indent
        .call(mergeParameters(fullPath, body, methodName, hasParameters))
        .outdent
        .add("}")
        .outdent
    } else printer.indent.call(generateBodyIfApplicable(body, methodName, "", hasParameters)).outdent
  }

  private def mergeParameters(
    fullPath: String,
    body: String,
    methodName: String,
    hasParameters: Boolean
  ): PrinterEndo = { printer =>
    pathVariableToJavaType.get(fullPath) match {
      case Some(subMap) =>
        val mergedParameters =
          subMap.foldLeft("") { case (result, (variableName, javaType)) =>
            val value = if (javaType == "STRING") s"List($variableName)" else s"List($variableName.toString)"
            val mapPair = s""""$variableName" -> $value"""
            if (result.isEmpty) mapPair else result + s", $mapPair"
          }
        printer
          .add(s"val parameters = Map($mergedParameters) ++ queryParameters")
          .call(generateBodyIfApplicable(body, methodName, "parameters", hasParameters))

      case None => printer.call(generateBodyIfApplicable(body, methodName, "queryParameters", hasParameters))
    }
  }

  private def generateBodyIfApplicable(
    body: String,
    methodName: String,
    queryParameterVariableName: String,
    hasParameters: Boolean
  ): PrinterEndo = { printer =>
    if (body.isBlank)
      printer.call(generateCallToDelegateFunction(methodName, queryParameterVariableName, hasParameters, hasBody = false))
    else
      printer
        .add("entity(as[String]) { body =>")
        .indent
        .call(generateCallToDelegateFunction(methodName, queryParameterVariableName, hasParameters, hasBody = true))
        .outdent
        .add("}")
  }

  private def generateCallToDelegateFunction(
    methodName: String,
    queryParameterVariableName: String,
    hasParameters: Boolean,
    hasBody: Boolean
  ): PrinterEndo = { printer =>
    val delegateFunctionName = GenerateDelegateFunctions.generateDelegateFunctionName(methodName)
    val parameters =
      if (hasBody && hasParameters) s"body, $queryParameterVariableName"
      else if (hasBody && !hasParameters) "body"
      else if (hasParameters) s"$queryParameterVariableName"
      else ""
    printer
      .when(!parameters.isBlank)(_.add(s"$delegateFunctionName($parameters)"))
      .when(parameters.isBlank)(_.add("???"))
  }

  private def parsePath(method: MethodDescriptor, fullPath: String) = {
    val paths = fullPath.split("/").filterNot(_.isBlank)
    val (pathSteps, pathVariables) =
      paths.foldLeft((Seq.empty[String], Seq.empty[String])) { case ((paths, pathMatchers), p) =>
        val (pathVariable, maybePathMatcher) =
          if (p.startsWith("{")) {
            val pathName = p.replaceAll("\\{", "").replaceAll("}", "")
            (extractFieldType(method, pathName, fullPath), Some(pathName))
          } else (s""""$p"""", None)

        val updatedPaths = (if (paths.nonEmpty) paths :+ "/" else paths) :+ s"""$pathVariable"""
        val updatedPathMatchers = pathMatchers ++ maybePathMatcher
        (updatedPaths, updatedPathMatchers)
      }

    val pathVariableStr =
      if (pathVariables.isEmpty) ""
      else if (pathVariables.size == 1) s" ${pathVariables.head} =>"
      else pathVariables.mkString(" (", ", ", ") =>")

    s"pathPrefix(${pathSteps.mkString(" ")}) {$pathVariableStr"
  }

  private def extractFieldType(method: MethodDescriptor, pathName: String, fullPath: String) = {
    val name = method.getName
    if (pathName.contains("\\.")) {
      throw new RuntimeException(s"""Path name contains ".",  methodName = $name, pathName = $pathName""")
    } else {
      val inputType = method.getInputType
      Option(inputType.findFieldByName(pathName)) match {
        case Some(fd) if fd.isMessage || fd.isMapField || fd.isRepeated =>
          throw new RuntimeException(s"""Field "$pathName" is not a singular field.""")

        case Some(fd) =>
          val javaType = fd.getJavaType.name()
          val updatedMap =
            pathVariableToJavaType.get(fullPath) match {
              case Some(valuesMap) => valuesMap + (pathName -> javaType)
              case None            => Map(pathName -> javaType)
            }
          pathVariableToJavaType += (fullPath -> updatedMap)
          javaTypeToPathMatcherMap(javaType)

        case None => throw new RuntimeException(s"Could not find field: methodName = $name, pathName = $pathName")
      }
    }
  }

  private def generateCompanionObject: PrinterEndo = { printer =>
    printer
      .add(s"object $handlerClassName {")
      .newline
      .indent
      .add("def apply(settings: GrpcClientSettings)(implicit sys: ClassicActorSystemProvider): GrpcGatewayHandler = {")
      .indent
      .add(s"new $handlerClassName(settings)")
      .outdent
      .add("}")
      .newline
      .add("def apply(clientName: String)(implicit sys: ClassicActorSystemProvider): GrpcGatewayHandler = {")
      .indent
      .add(s"$handlerClassName(GrpcClientSettings.fromConfig(clientName))")
      .outdent
      .add("}")
      .outdent
      .add("}")
  }
}
