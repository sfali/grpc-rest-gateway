package com.improving
package grpc_rest_gateway
package compiler
package akka_pekko

import com.google.protobuf.Descriptors.ServiceDescriptor
import compiler.utils.GenerateDelegateFunctions
import scalapb.compiler.FunctionalPrinter.PrinterEndo
import scalapb.compiler.{DescriptorImplicits, FunctionalPrinter}

class GatewayHandlerPrinter(packageNamePrefix: String, service: ServiceDescriptor, implicits: DescriptorImplicits)
    extends HandlerPrinter {
  import implicits.*

  private val extendedFileDescriptor = ExtendedFileDescriptor(service.getFile)
  private val serviceName = service.getName
  private val specificationName = getProtoFileName(extendedFileDescriptor.file.getName)
  private val scalaPackageName = extendedFileDescriptor.scalaPackage.fullName
  private val handlerClassName = serviceName + "GatewayHandler"
  private val clientClasName = s"${serviceName}Client"
  protected override val outputFileName: String = scalaPackageName.replace('.', '/') + "/" + handlerClassName + ".scala"
  private val wildcardImport = extendedFileDescriptor.V.WildcardImport
  private val methods = getUnaryCallsWithHttpExtension(service).toList

  override protected val content: String =
    new FunctionalPrinter()
      .add("/*", " * Generated by GRPC-REST gateway compiler. DO NOT EDIT.", " */")
      .add(s"package $scalaPackageName")
      .newline
      .add(
        s"import com.improving.grpc_rest_gateway.runtime",
        s"import runtime.core.$wildcardImport",
        s"import runtime.handlers.GrpcGatewayHandler"
      )
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
    _.add(s"class $handlerClassName(settings: GrpcClientSettings)(implicit sys: ClassicActorSystemProvider) extends GrpcGatewayHandler {")
      .newline
      .indent
      .add("private implicit val ec: ExecutionContext = sys.classicSystem.dispatcher")
      .add(s"private lazy val client = $clientClasName(settings)")
      .add(s"""override val specificationName: String = "$specificationName"""")
      .newline
      .call(RouteGenerator(implicits, methods))
      .outdent // do we have an extra indent somewhere?
      .call(GenerateDelegateFunctions(implicits, "completeResponse", methods))
      .outdent
      .add("}")

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
