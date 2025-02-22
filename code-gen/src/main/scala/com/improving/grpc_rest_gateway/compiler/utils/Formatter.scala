package com.improving
package grpc_rest_gateway
package compiler
package utils

import java.nio.file.{Files, Paths, StandardCopyOption}
import java.util.jar.JarFile
import scala.util.{Failure, Success, Try}

object Formatter {

  private val DefaultScalafmtFile = "default.scalafmt.conf"

  def format(content: String): String =
    getScalaFmtConfig match {
      case Some(config) =>
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

      case None => content
    }

  private def getScalaFmtConfig = {
    val uri = this.getClass.getClassLoader.getResource(DefaultScalafmtFile).toURI
    uri.getScheme match {
      case "file" => Some(Paths.get(uri))
      case "jar"  =>
        // scalafmt can't access a file inside a JAR so we'll copy the content into a temp file
        val jar = new JarFile(this.getClass.getProtectionDomain.getCodeSource.getLocation.toURI.getPath)
        val file = Files.createTempFile(null, null)
        val scalafmtConfig = jar.getInputStream(jar.getEntry(DefaultScalafmtFile))
        Files.copy(scalafmtConfig, file, StandardCopyOption.REPLACE_EXISTING)
        Some(file)

      case _ => None
    }
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
    val reporter = constructor.newInstance(System.err).asInstanceOf[ScalafmtReporter]
    fmt.withReporter(reporter)
  }
}
