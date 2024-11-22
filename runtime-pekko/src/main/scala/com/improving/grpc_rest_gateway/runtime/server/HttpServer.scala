package com.improving
package grpc_rest_gateway
package runtime
package server

import runtime.handlers.{GrpcGatewayHandler, SwaggerHandler}
import org.apache.pekko
import pekko.actor.ClassicActorSystemProvider
import pekko.http.scaladsl.Http
import pekko.http.scaladsl.server.Directives.*

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object HttpServer {

  def apply(
    host: String,
    port: Int,
    handlers: GrpcGatewayHandler*
  )(implicit
    sys: ClassicActorSystemProvider
  ): Unit = {
    implicit val ec: ExecutionContext = sys.classicSystem.dispatcher
    val routes = SwaggerHandler(handlers).route +: handlers.map(_.route)
    Http()
      .newServerAt(host, port)
      .bind(concat(routes*))
      .onComplete {
        case Failure(ex) =>
          Console
            .err
            .println(
              s"${Console.RED}Failed to bind HTTP endpoint at $host:$port, reason=${ex.getClass.getName}:${ex.getMessage}${Console.RESET}"
            )
        case Success(binding) =>
          Console.out.println(s"${Console.BOLD}Http server started at ${binding.localAddress}${Console.RESET}")
      }
  }
}
