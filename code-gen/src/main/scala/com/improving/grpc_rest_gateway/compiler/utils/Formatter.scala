package com.improving
package grpc_rest_gateway
package compiler
package utils

import java.nio.file.{Files, Paths}
import scala.util.{Failure, Success, Try}

object Formatter {

  def format(content: String): String = {
    val config = Paths.get(".scalafmt.conf")
    if (Files.exists(config)) {
      Try {
        val scalaFmt = buildScalaFmt()
        val path = Files.createTempFile("Codegen", ".scala")
        val result = scalaFmt.format(config, path, content)
        Try(Files.delete(path))
        scalaFmt.clear()
        result
      } match {
        case Failure(ex) =>
          Console.err.println(s"Unable to format generated code: ${ex.getClass.getName}:${ex.getMessage}")
          content
        case Success(value) => value
      }
    } else content
  }

  private def buildScalaFmt() = {
    import coursierapi.{Dependency, Fetch}
    import org.scalafmt.interfaces.{Scalafmt, ScalafmtReporter}

    import java.io.PrintStream
    import java.net.URLClassLoader
    import scala.jdk.CollectionConverters.*

    val scalaVersion = BuildInfo.scalaPartialVersion match {
      case Some((2, 12)) => "2.12"
      case Some((2, 13)) => "2.13"
      case Some((3, _))  => "2.13"
      case _             => "2.12"
    }

    val files = Fetch
      .create()
      .addDependencies(Dependency.of("org.scalameta", s"scalafmt-dynamic_$scalaVersion", BuildInfo.scalafmtVersion))
      .fetch()
    val classLoader = new URLClassLoader(files.asScala.toArray.map(_.toURI.toURL()), this.getClass.getClassLoader)
    val fmt = Scalafmt.create(classLoader)
    val reporterClass = classLoader.loadClass("org.scalafmt.dynamic.ConsoleScalafmtReporter")
    val constructor = reporterClass.getConstructor(classOf[PrintStream]);
    val reporter: ScalafmtReporter = constructor.newInstance(System.err).asInstanceOf[ScalafmtReporter]
    fmt.withReporter(reporter)
  }
}
