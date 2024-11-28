package com.improving
package grpc_rest_gateway
package runtime
package server

import com.typesafe.config.Config
import runtime.handlers.{GrpcGatewayHandler, SwaggerHandler}
import org.apache.pekko
import org.slf4j.LoggerFactory
import pekko.actor.ClassicActorSystemProvider
import pekko.http.scaladsl.Http
import pekko.http.scaladsl.server.Directives.*

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

class GatewayServer(
  host: String,
  port: Int,
  handlers: GrpcGatewayHandler*
)(implicit
  sys: ClassicActorSystemProvider) {

  private val logger = LoggerFactory.getLogger(classOf[GatewayServer])

  def run(): Unit = {
    implicit val ec: ExecutionContext = sys.classicSystem.dispatcher
    val routes = SwaggerHandler(handlers).route +: handlers.map(_.route)
    Http()
      .newServerAt(host, port)
      .bind(concat(routes*))
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
  }

}

object GatewayServer {
  def apply(
    host: String,
    port: Int,
    handlers: GrpcGatewayHandler*
  )(implicit
    sys: ClassicActorSystemProvider
  ): GatewayServer = new GatewayServer(host, port, handlers*)

  def apply(
    config: Config,
    handlers: GrpcGatewayHandler*
  )(implicit
    sys: ClassicActorSystemProvider
  ): GatewayServer = GatewayServer(config.getString("host"), config.getInt("port"), handlers*)
}
