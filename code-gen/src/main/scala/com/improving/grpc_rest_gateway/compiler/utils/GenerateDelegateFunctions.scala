package com.improving
package grpc_rest_gateway
package compiler
package utils

import com.google.api.HttpRule.PatternCase
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType
import com.google.protobuf.Descriptors.{Descriptor, FieldDescriptor, MethodDescriptor}
import scalapb.compiler.DescriptorImplicits
import scalapb.compiler.FunctionalPrinter.PrinterEndo

import scala.jdk.CollectionConverters.*

class GenerateDelegateFunctions private[utils] (
  implicits: DescriptorImplicits,
  responseFunctionName: String,
  methods: List[MethodDescriptor]) {
  import implicits.*
  import GenerateDelegateFunctions.*

  private def generateMethodHandlerDelegates: PrinterEndo = { printer =>
    methods.foldLeft(printer) { case (printer, method) =>
      val paths = extractPaths(method).groupBy(_._1).map { case (patternCase, seq) =>
        patternCase -> seq.map { case (_, path, body) =>
          (path, body)
        }
      }
      paths.foldLeft(printer) { case (printer, (patternCase, pathNBodies)) =>
        // for a given method having a multiple GET make sense, if we are to have multiple paths for POST and PUT
        // then we have a problem, for now log the error and process first one
        if (patternCase == PatternCase.PUT || patternCase == PatternCase.POST) {
          if (pathNBodies.size > 1) {
            Console
              .err
              .println(s"${Console.RED} Multiple paths found for $patternCase and ${method.getName}  ${Console.RESET}")
            printer
          } else printer.call(generateMethodHandlerDelegate(method, patternCase, pathNBodies.head._2))
        } else printer.call(generateMethodHandlerDelegate(method, patternCase))
      }
    }
  }

  private def generateMethodHandlerDelegate(
    method: MethodDescriptor,
    httpMethod: PatternCase,
    body: String = ""
  ): PrinterEndo = { printer =>
    val name = method.getName
    val methodName = name.charAt(0).toLower + name.substring(1)
    val delegateFunctionName = generateDelegateFunctionName(name)
    val serviceFunctionName = method.getInputType.scalaType.fullName
    httpMethod match {
      case PatternCase.GET | PatternCase.DELETE | PatternCase.PATCH =>
        printer
          .newline
          .add(s"""private def $delegateFunctionName(statusCode: Int, parameters: Map[String, Seq[String]]) = {""")
          .indent
          .add("val input = Try {")
          .indent
          .call(generateInputFromQueryString(method.getInputType, serviceFunctionName, required = true))
          .outdent
          .add("}")
          .add(s"$responseFunctionName(input, client.$methodName, statusCode)")
          .outdent
          .add("}")
      case PatternCase.PUT | PatternCase.POST =>
        printer.call(
          generateBodyParam(
            method = method,
            delegateFunctionName = delegateFunctionName,
            serviceFunctionName = serviceFunctionName,
            methodName = methodName,
            body = body
          )
        )
      case _ => printer
    }
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

  private def generateBodyParam(
    method: MethodDescriptor,
    delegateFunctionName: String,
    serviceFunctionName: String,
    methodName: String,
    body: String
  ): PrinterEndo = { printer =>
    if (body.nonEmpty) {
      if (body == "*") {
        printer
          .newline
          .add(s"""private def $delegateFunctionName(statusCode: Int, body: String) = {""")
          .indent
          .add(
            s"val input = parseBody[$serviceFunctionName](body)"
          )
          .add(s"$responseFunctionName(input, client.$methodName, statusCode)")
          .outdent
          .add("}")
      } else {
        val inputTypeDescriptor = method.getInputType
        val maybeDescriptor = inputTypeDescriptor.getFields.asScala.find(_.getName == body)
        maybeDescriptor match {
          case Some(descriptor) =>
            val fd = ExtendedFieldDescriptor(descriptor)
            val bodyFullType = descriptor.getMessageType.scalaType.fullName
            val optional = !fd.noBox // this is an Option in parent type
            val args =
              inputTypeDescriptor.getFields.asScala.map(f => s"${f.getJsonName} = ${f.getJsonName}").mkString(", ")
            printer
              .newline
              .add(
                s"""private def $delegateFunctionName(statusCode: Int, body: String, parameters: Map[String, Seq[String]]) = {"""
              )
              .indent
              .when(optional)(_.add(s"val parsedBody = parseBodyOptional[$bodyFullType](body)"))
              .when(!optional)(_.add(s"val parsedBody = parseBody[$bodyFullType](body)"))
              .add("val input = Try {")
              .indent
              .call(generateInputFromQueryStringSingle(inputTypeDescriptor, required = true, ignoreFieldName = body))
              .add(s"""val $body = parsedBody.get""")
              .add(s"$serviceFunctionName($args)")
              .outdent
              .add("}")
              .add(s"$responseFunctionName(input, client.$methodName, statusCode)")
              .outdent
              .add("}")
          case None =>
            throw new RuntimeException(
              s"Unable to determine body type for input: $serviceFunctionName, body: $body, method: $methodName"
            )
        }
      }
    } else
      throw new RuntimeException(
        s"Body parameter is empty for input: $serviceFunctionName, method: $methodName"
      )
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
          val fullScalaTypeName = f.getMessageType.scalaType.fullName
          if (fullScalaTypeName.startsWith("com.google.protobuf.wrappers")) {
            // Google primitive type wrapper has special handling
            generatePrimitiveTypeWrappers(f.getMessageType.getFields.asScala.head, s"$prefix$inputName", jsonName)(p)
          } else {
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
          }

        case JavaType.ENUM =>
          p.when(f.isRepeated)(
            _.add(s"""val $jsonName = parameters.toEnumValues("$prefix$inputName", ${f.singleScalaTypeName})""")
          ).when(!f.isRepeated)(
            _.add(
              s"""val $jsonName = parameters.toEnumValue("$prefix$inputName", ${f.singleScalaTypeName})"""
            )
          )

        case JavaType.BOOLEAN =>
          if (f.isRepeated) p.add(s"""val $jsonName = parameters.toBooleanValues("$prefix$inputName")""")
          else
            p.add(s"""val $jsonName = parameters.toBooleanValue("$prefix$inputName")""")

        case JavaType.DOUBLE =>
          if (f.isRepeated) p.add(s"""val $jsonName = parameters.toDoubleValues("$prefix$inputName")""")
          else
            p.when(required)(_.add(s"""val $jsonName = parameters.toDoubleValue("$prefix$inputName")"""))
              .when(!required)(_.add(s"""val $jsonName = parameters.toDoubleValue("$prefix$inputName", "")"""))

        case JavaType.FLOAT =>
          if (f.isRepeated) p.add(s"""val $jsonName = parameters.toFloatValues("$prefix$inputName")""")
          else
            p.when(required)(_.add(s"""val $jsonName = parameters.toFloatValue("$prefix$inputName")"""))
              .when(!required)(_.add(s"""val $jsonName = parameters.toFloatValue("$prefix$inputName", "")"""))

        case JavaType.INT =>
          if (f.isRepeated)
            p.add(s"""val $jsonName = parameters.toIntValues("$prefix$inputName")""")
          else
            p.when(required)(_.add(s"""val $jsonName = parameters.toIntValue("$prefix$inputName")"""))
              .when(!required)(_.add(s"""val $jsonName = parameters.toIntValue("$prefix$inputName", "")"""))

        case JavaType.LONG =>
          if (f.isRepeated) p.add(s"""val $jsonName = parameters.toLongValues("$prefix$inputName")""")
          else
            p.when(required)(_.add(s"""val $jsonName = parameters.toLongValue("$prefix$inputName")"""))
              .when(!required)(_.add(s"""val $jsonName = parameters.toLongValue("$prefix$inputName", "")"""))

        case JavaType.STRING =>
          if (f.isRepeated) p.add(s"""val $jsonName = parameters.toStringValues("$prefix$inputName")""")
          else
            p.add(s"""val $jsonName = parameters.toStringValue("$prefix$inputName")""")
        case jt => throw new Exception(s"Unknown java type: $jt")
      }
    }

  private def getInputName(d: FieldDescriptor, prefix: String = ""): String = {
    val name = prefix.split(".").filter(_.nonEmpty).map(s => s.charAt(0).toUpper + s.substring(1)).mkString + d.getName
    name.charAt(0).toLower + name.substring(1)
  }

  private def generatePrimitiveTypeWrappers(
    fd: FieldDescriptor,
    variableName: String,
    parentFieldName: String
  ): PrinterEndo = { printer =>
    fd.getJavaType match {
      case JavaType.INT =>
        printer.add(
          s"""val $variableName = Try(parameters.toIntValue("$parentFieldName.${fd.getName}", "")).toOption"""
        )
      case JavaType.LONG =>
        printer.add(
          s"""val $variableName = Try(parameters.toLongValue("$parentFieldName.${fd.getName}", "")).toOption"""
        )
      case JavaType.FLOAT =>
        printer.add(
          s"""val $variableName = Try(parameters.toFloatValue("$parentFieldName.${fd.getName}", "")).toOption"""
        )
      case JavaType.DOUBLE =>
        printer.add(
          s"""val $variableName = Try(parameters.toDoubleValue("$parentFieldName.${fd.getName}", "")).toOption"""
        )
      case JavaType.BOOLEAN =>
        printer.add(
          s"""val $variableName = Try(parameters.toBooleanValue("$parentFieldName.${fd.getName}", "")).toOption"""
        )
      case JavaType.STRING =>
        printer.add(
          s"""val $variableName = Try(parameters.toStringValue("$parentFieldName.${fd.getName}", "")).toOption"""
        )
      case _ => printer
    }
  }
}

object GenerateDelegateFunctions {
  def apply(
    implicits: DescriptorImplicits,
    responseFunctionName: String,
    methods: List[MethodDescriptor]
  ): PrinterEndo =
    new GenerateDelegateFunctions(implicits, responseFunctionName, methods).generateMethodHandlerDelegates

  def generateDelegateFunctionName(methodName: String): String = s"dispatch$methodName"
}
