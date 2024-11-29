package com.improving
package grpc_rest_gateway
package compiler

import com.google.api.AnnotationsProto
import com.google.protobuf.ExtensionRegistry
import protocgen.{CodeGenApp, CodeGenRequest, CodeGenResponse}
import scalapb.compiler.{DescriptorImplicits, GeneratorParams}
import scalapb.options.Scalapb

import scala.jdk.CollectionConverters.*

object GatewayGenerator extends CodeGenApp {

  private val Netty = "netty"
  private val Pekko = "pekko"

  override def registerExtensions(registry: ExtensionRegistry): Unit = {
    Scalapb.registerAllExtensions(registry)
    AnnotationsProto.registerAllExtensions(registry)
  }

  override def process(request: CodeGenRequest): CodeGenResponse =
    GeneratorParams.fromStringCollectUnrecognized(request.parameter) match {
      case Right((params, options)) =>
        val implementationType =
          if (options.isEmpty) Some(Netty)
          else
            options
              .collectFirst {
                case option if option.startsWith("implementationType:") =>
                  val separator = option.indexOf(":")
                  val value = option.substring(separator + 1)
                  value match {
                    case Pekko => value
                    case _     => Netty
                  }
              }
              .getOrElse(Netty)

        Console
          .out
          .println(
            s"${Console.RED}Generating GatewayHandler implementation for '${Console.BOLD}$implementationType${Console.RESET}'.${Console.RESET}"
          )
        // Implicits gives you extension methods that provide ScalaPB names and types
        // for protobuf entities.
        val implicits = DescriptorImplicits.fromCodeGenRequest(params, request)

        CodeGenResponse.succeed(
          request
            .filesToGenerate
            .flatMap(_.getServices.asScala)
            .map { sd =>
              implementationType match {
                case Pekko => new akka_pekko.GatewayHandlerPrinter(Pekko, sd, implicits)
                case _     => new netty.GatewayHandlerPrinter(sd, implicits)
              }
            }
            .map(_.result)
        )

      case Left(error) => CodeGenResponse.fail(error)
    }
}
