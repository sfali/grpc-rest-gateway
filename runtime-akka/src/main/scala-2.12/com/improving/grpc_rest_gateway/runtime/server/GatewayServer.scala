package com.improving
package grpc_rest_gateway
package runtime
package server

import com.typesafe.config.Config
import runtime.handlers.{GrpcGatewayHandler, SwaggerHandler}
import runtime.core.HttpSettings
import org.slf4j.LoggerFactory
import akka.actor.ClassicActorSystemProvider
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives.*

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class GatewayServer(
  host: String,
  port: Int,
  hardTerminationDeadLine: FiniteDuration,
  handlers: GrpcGatewayHandler*
)(implicit
  sys: ClassicActorSystemProvider) {

  private val logger = LoggerFactory.getLogger(classOf[GatewayServer])

  def run(): Future[Http.ServerBinding] = {
    implicit val ec: ExecutionContext = sys.classicSystem.dispatcher
    val routes = SwaggerHandler(handlers).route +: handlers.map(_.route)
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
          logger.info("Http server started at http://{}:{}", localAddress.getHostString, localAddress.getPort)
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
  )(implicit
    sys: ClassicActorSystemProvider
  ): GatewayServer = new GatewayServer(host, port, hardTerminationDeadLine, handlers*)

  def apply(
    httpSettings: HttpSettings,
    handlers: GrpcGatewayHandler*
  )(implicit
    sys: ClassicActorSystemProvider
  ): GatewayServer = GatewayServer(httpSettings.host, httpSettings.port, httpSettings.hardTerminationDeadline, handlers*)

  def apply(
    config: Config,
    handlers: GrpcGatewayHandler*
  )(implicit
    sys: ClassicActorSystemProvider
  ): GatewayServer = GatewayServer(HttpSettings(config), handlers*)
}
