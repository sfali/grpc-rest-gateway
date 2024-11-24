package com.improving
package grpc_rest_gateway
package compiler
package utils

import com.google.protobuf.Descriptors.{FieldDescriptor, MethodDescriptor}
import com.google.protobuf.WireFormat.JavaType
import scalapb.compiler.DescriptorImplicits
import scalapb.compiler.FunctionalPrinter.PrinterEndo

import scala.annotation.tailrec
import scala.jdk.CollectionConverters.*

class GenerateImportStatements private[utils] (
  currentPackageName: String,
  implicits: DescriptorImplicits,
  methods: List[MethodDescriptor]) {
  import implicits.*

  private def createImportStatements: PrinterEndo =
    _.print(buildImportStatementsMap(methods).toSeq.sortBy(_._1).filterNot(_._1 == currentPackageName)) {
      case (p, (packageName, classNames)) =>
        if (classNames.size == 1) p.add(s"import $packageName.${classNames.head}")
        else {
          val sortedClassNames = classNames.toSeq.sorted
          p.add(s"""import $packageName.${sortedClassNames.mkString("{", ", ", "}")}""")
        }
    }

  @tailrec
  private def buildImportStatementsMap(
    methods: List[MethodDescriptor],
    result: Map[String, Set[String]] = Map.empty[String, Set[String]]
  ): Map[String, Set[String]] =
    methods match {
      case Nil => result
      case method :: tail =>
        val updatedMap = updateMap(ExtendedMethodDescriptor(method).inputType.scalaType, result)
        val inputType = method.getInputType
        val fields = extractSubFields(inputType.getFields.asScala.toList)
        buildImportStatementsMap(tail, buildImportStatementsMapFromFields(fields, updatedMap))
    }

  private def extractSubFields(fields: List[FieldDescriptor]) =
    fields.filter { fd =>
      val efd = ExtendedFieldDescriptor(fd)
      efd.isMessage || efd.isEnum
    }

  @tailrec
  private def buildImportStatementsMapFromFields(
    fields: List[FieldDescriptor],
    result: Map[String, Set[String]]
  ): Map[String, Set[String]] =
    fields match {
      case Nil => result
      case field :: tail =>
        val updatedMap = updateMap(field.singleScalaTypeName, result)
        val subFields =
          if (field.getJavaType == JavaType.MESSAGE)
            extractSubFields(field.getMessageType.getFields.asScala.toList)
          else Nil
        buildImportStatementsMapFromFields(tail ::: subFields, updatedMap)
    }

  private def updateMap(fqn: String, result: Map[String, Set[String]]) = {
    val (packageName, className) = getPackageNClassNameTuple(fqn)
    val updatedValues =
      result.get(packageName) match {
        case Some(set) => set + className
        case None      => Set(className)
      }
    result + (packageName -> updatedValues)
  }

  private def getPackageNClassNameTuple(fqn: String) = {
    val indexOfSeparator = fqn.lastIndexOf(".")
    val className = fqn.substring(indexOfSeparator + 1)
    val packageName = if (indexOfSeparator >= 0) fqn.substring(0, indexOfSeparator) else ""
    packageName -> className
  }
}

object GenerateImportStatements {
  def apply(currentPackageName: String, implicits: DescriptorImplicits, methods: List[MethodDescriptor]): PrinterEndo =
    new GenerateImportStatements(currentPackageName, implicits, methods).createImportStatements
}
