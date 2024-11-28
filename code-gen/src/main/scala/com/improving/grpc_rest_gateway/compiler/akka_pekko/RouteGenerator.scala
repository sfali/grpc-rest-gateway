package com.improving
package grpc_rest_gateway
package compiler
package akka_pekko

import com.google.protobuf.Descriptors.MethodDescriptor
import com.improving.grpc_rest_gateway.compiler.utils.GenerateDelegateFunctions
import compiler.utils.path_parser.{MethodInfo, PathParserUtils, TreeNode}
import scalapb.compiler.DescriptorImplicits
import scalapb.compiler.FunctionalPrinter.PrinterEndo

private class RouteGenerator(implicits: DescriptorImplicits, methods: List[MethodDescriptor]) {

  import implicits.*
  import RouteGenerator.*

  private val rawValues = methods.flatMap { method =>
    extractPaths(method).map { case (patternCase, path, body) =>
      (path, patternCase, body, method)
    }
  }

  private val pathTree =
    PathParserUtils.buildTree(rawValues).toList.sortBy(_._1).zipWithIndex.map { case ((_, treeNode), index) =>
      (treeNode, index)
    }
  private val totalRootNodes = pathTree.size
  private val javaTypeToPathMatcherMap =
    Map("INT" -> "IntNumber", "LONG" -> "LongNumber", "DOUBLE" -> "DoubleNumber").withDefaultValue("Segment")
  private val pathVariableToFieldMap = extractFieldTypes

  private def generateRoute: PrinterEndo =
    _.add("override val route: Route = handleExceptions(exceptionHandler) {")
      .indent
      .when(totalRootNodes > 1)(_.add("concat(").indent)
      .indent
      .print(pathTree) { case (p, (treeNode, index)) =>
        generateRouteTree(treeNode, index, totalRootNodes, parentHasMethods = false)(p)
      }
      .when(totalRootNodes > 1)(_.outdent.add(")"))
      .add("}")

  private def generateRouteTree(
    treeNode: TreeNode[MethodDescriptor],
    currentIndex: Int,
    totalPaths: Int,
    parentHasMethods: Boolean
  ): PrinterEndo = { printer =>
    val children = treeNode.children.values.zipWithIndex
    val totalChildPaths = children.size
    val hasMethods = treeNode.methodInfos.nonEmpty
    val hasChildPaths = children.nonEmpty
    val isLastChild = totalPaths == (currentIndex + 1)
    val concatRoute = (hasMethods && hasChildPaths) || children.size > 1

    val path = treeNode.path
    val pathPrefix =
      if (path.startsWith("{")) {
        val pathVariableInfo = getPathVariableInfo(treeNode)
        s"""pathPrefix(${pathVariableInfo.pathMatcherName}) { ${pathVariableInfo.variableName} => """
      } else s"""pathPrefix("$path") {"""

    printer
      .add(pathPrefix)
      .indent
      .when(concatRoute)(_.add("concat(").indent)
      .when(hasMethods)(_.call(generateMethods(treeNode.methodInfos, hasChildPaths)))
      .print(children) { case (p, (childNode, index)) =>
        generateRouteTree(childNode, index, totalChildPaths, hasMethods)(p)
      }
      .when(concatRoute)(_.outdent.add(")"))
      .when(totalPaths > 1 && !isLastChild)(_.add("},"))
      .when(isLastChild)(_.add("}"))
      .outdent
  }

  private def generateMethods(methodInfos: List[MethodInfo[MethodDescriptor]], hasChildPaths: Boolean): PrinterEndo = {
    printer =>
      val methods = methodInfos.zipWithIndex
      val totalMethods = methods.size
      printer
        .add("pathEnd {")
        .indent
        .when(totalMethods > 1)(_.add("concat(").indent)
        .print(methods) { case (p, (methodInfo, index)) => generateMethod(methodInfo, index, totalMethods)(p) }
        .when(totalMethods > 1)(_.add(")").outdent)
        .outdent
        .when(hasChildPaths)(_.add("},"))
        .when(!hasChildPaths)(_.add("}"))

  }

  private def generateMethod(
    methodInfo: MethodInfo[MethodDescriptor],
    currentIndex: Int,
    totalMethods: Int
  ): PrinterEndo = { printer =>
    val isLastMethod = totalMethods == (currentIndex + 1)
    printer
      .add(s"${methodInfo.method} {")
      .indent
      .call(generateParametersIfApplicable(methodInfo.fullPath, methodInfo.body, methodInfo.source.getName))
      .outdent
      .when(isLastMethod)(_.add("}"))
      .when(!isLastMethod)(_.add("},"))
  }

  // all methods in this branch should have belonged to same path
  private def getPathVariableInfo(treeNode: TreeNode[MethodDescriptor]) = {
    val path = sanitizePath(treeNode.path)
    treeNode.methodInfos.collectFirst { case mi =>
      mi.fullPath
    } match {
      case Some(fullPath) =>
        pathVariableToFieldMap.get(fullPath) match {
          case Some(valuesMap) =>
            valuesMap.get(path) match {
              case Some(value) => value
              case None        => throw new RuntimeException(s"Unable to get PathVariableInfo for: $fullPath and $path")
            }
          case None => throw new RuntimeException(s"Unable to get map for: $fullPath")
        }
      case None => throw new RuntimeException(s"Unable to get full path for path name: ${treeNode.path}")
    }
  }

  private def extractFieldTypes =
    rawValues.foldLeft(Map.empty[String, Map[String, PathVariableInfo]]) { case (result, (fullPath, _, _, method)) =>
      val pathVariables = fullPath
        .split("/")
        .filterNot(_.isBlank)
        .filter(_.startsWith("{"))
        .map(_.replaceAll("\\{", "").replaceAll("}", ""))
      extractFieldsFromMethod(fullPath, pathVariables, method, result)
    }

  private def extractFieldsFromMethod(
    fullPath: String,
    pathVariables: Seq[String],
    method: MethodDescriptor,
    result: Map[String, Map[String, PathVariableInfo]]
  ) =
    pathVariables.foldLeft(result) { case (finalResult, pathName) =>
      val name = method.getName
      if (pathName.contains("\\.")) {
        throw new RuntimeException(s"""Path name contains ".",  methodName = $name, pathName = $pathName""")
      } else {
        val inputType = method.getInputType
        val updatedMap =
          Option(inputType.findFieldByName(pathName)) match {
            case Some(fd) if fd.isMessage || fd.isMapField || fd.isRepeated =>
              throw new RuntimeException(s"""Field "$pathName" is not a singular field.""")

            case Some(fd) =>
              val javaType = fd.getJavaType.name()
              val pathMatcherName = javaTypeToPathMatcherMap(javaType)
              finalResult.get(fullPath) match {
                case Some(valuesMap) =>
                  // same path can be used for multiple methods, a path variable should have same data type,
                  // if we don't then we have a problem, e.g., in get it is defined as String and in post it is defined as Long,
                  // in order to avoid compilation we keep String data type
                  valuesMap.get(pathName) match {
                    case Some(value)
                        if value.pathMatcherName == pathMatcherName || value.pathMatcherName == "Segment" =>
                      valuesMap
                    case None => valuesMap + (pathName -> PathVariableInfo(pathName, pathMatcherName, javaType))
                  }

                case None => Map(pathName -> PathVariableInfo(pathName, pathMatcherName, javaType))
              }

            case None => throw new RuntimeException(s"Could not find field: methodName = $name, pathName = $pathName")
          }
        finalResult + (fullPath -> updatedMap)
      }
    }

  private def sanitizePath(path: String) = path.replaceAll("\\{", "").replaceAll("}", "")

  // FUNCTIONS FOR GENERATING CODE WITHIN HTTP METHODS

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
    pathVariableToFieldMap.get(fullPath) match {
      case Some(subMap) =>
        val mergedParameters =
          subMap.foldLeft("") { case (result, (_, PathVariableInfo(variableName, _, javaType))) =>
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
}

object RouteGenerator {
  def apply(implicits: DescriptorImplicits, methods: List[MethodDescriptor]): PrinterEndo =
    new RouteGenerator(implicits, methods).generateRoute

  /** @param variableName
    *   This is name of the field defined in proto file, e.g., `message_id`.
    * @param pathMatcherName
    *   This Akka / Pekko path matcher, one of IntNumber, LongNumber, DoubleNumber, or Segment.
    * @param javaType
    *   Java type, one of INT, LONG, DOUBLE, or String.
    */
  private case class PathVariableInfo(variableName: String, pathMatcherName: String, javaType: String)
}
