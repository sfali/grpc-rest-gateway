package com.improving
package grpc_rest_gateway
package compiler
package utils

import org.scalafmt.interfaces.Scalafmt

import java.nio.file.{Files, Paths}
import scala.util.Try

class Formatter {

  def format(content: String): String = {
    val config = Paths.get(".scalafmt.conf")
    if (Files.exists(config)) {
      Try {
        val scalaFmt = Scalafmt.create(getClass.getClassLoader)
        val path = Files.createTempFile("Codegen", ".scala")
        val result = scalaFmt.format(config, path, content)
        Try(Files.delete(path))
        scalaFmt.clear()
        result
      }.getOrElse(content)
    } else content
  }
}

object Formatter {
  def apply(): Formatter = new Formatter
}
