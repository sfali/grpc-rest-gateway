package com.improving
package grpc_rest_gateway
package runtime
package server

import com.typesafe.config.Config
import runtime.handlers.{GrpcGatewayHandler, SwaggerHandler}
import runtime.core.{HttpSettings, OpenApiSettings}
import org.apache.pekko
import org.slf4j.LoggerFactory
import pekko.actor.ClassicActorSystemProvider
import pekko.http.scaladsl.Http
import pekko.http.scaladsl.server.Directives.*

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class GatewayServer(
  host: String,
  port: Int,
  hardTerminationDeadLine: FiniteDuration,
  handlers: GrpcGatewayHandler*
)(using
  sys: ClassicActorSystemProvider) {

  private val logger = LoggerFactory.getLogger(classOf[GatewayServer])
  private val openApiSettings = OpenApiSettings(sys.classicSystem.settings.config.getConfig("openapi"))

  def run(): Future[Http.ServerBinding] = {
    given ec: ExecutionContext = sys.classicSystem.dispatcher
    val handlerRoutes = handlers.map(_.route)
    val routes =
      if openApiSettings.enabled then
        handlerRoutes :+ SwaggerHandler(openApiSettings.specsDirectory, handlers.map(_.specificationName)).route
      else handlerRoutes
    val eventualBinding = Http()
      .newServerAt(host, port)
      .bind(concat(routes*))
      .map(_.addToCoordinatedShutdown(hardTerminationDeadline = hardTerminationDeadLine))

    eventualBinding
      .onComplete {
        case Failure(ex) =>
          logger.warn(
            "Failed to bind HTTP endpoint at {}:{}, reason={}:{}",
            host,
            port.toString,
            ex.getClass.getName,
            ex.getMessage
          )
        case Success(binding) =>
          val localAddress = binding.localAddress
          logger.info("Gateway server started at http://{}:{}", localAddress.getHostString, localAddress.getPort)
      }

    eventualBinding
  }

}

object GatewayServer {
  def apply(
    host: String,
    port: Int,
    hardTerminationDeadLine: FiniteDuration,
    handlers: GrpcGatewayHandler*
  )(using
    sys: ClassicActorSystemProvider
  ): GatewayServer = new GatewayServer(host, port, hardTerminationDeadLine, handlers*)

  def apply(
    httpSettings: HttpSettings,
    handlers: GrpcGatewayHandler*
  )(using
    sys: ClassicActorSystemProvider
  ): GatewayServer = GatewayServer(httpSettings.host, httpSettings.port, httpSettings.hardTerminationDeadline, handlers*)

  def apply(
    config: Config,
    handlers: GrpcGatewayHandler*
  )(using
    sys: ClassicActorSystemProvider
  ): GatewayServer = GatewayServer(HttpSettings(config), handlers*)
}
