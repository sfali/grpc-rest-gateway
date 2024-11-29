package com.improving.grpc_rest_gateway.compiler

import com.google.api.AnnotationsProto
import com.google.protobuf.ExtensionRegistry
import com.improving.grpc_rest_gateway.compiler.akka_pekko.GatewayHandlerPrinter
import protocbridge.Artifact
import protocgen.{CodeGenApp, CodeGenRequest, CodeGenResponse}
import scalapb.compiler.{DescriptorImplicits, ProtobufGenerator}
import scalapb.options.Scalapb

import scala.jdk.CollectionConverters.*

object AkkaGatewayGenerator extends CodeGenApp {

  override def registerExtensions(registry: ExtensionRegistry): Unit = {
    Scalapb.registerAllExtensions(registry)
    AnnotationsProto.registerAllExtensions(registry)
  }

  override def suggestedDependencies: Seq[Artifact] = Seq(
    Artifact(
      groupId = BuildInfo.organizationName,
      artifactId = "grpc-rest-gateway-runtime-akka",
      version = BuildInfo.version,
      crossVersion = true
    ).asSbtPlugin(BuildInfo.scalaVersion, BuildInfo.sbtVersion)
  )

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
          } yield new GatewayHandlerPrinter("akka", sd, implicits).result
        )

      case Left(error) => CodeGenResponse.fail(error)
    }
}
