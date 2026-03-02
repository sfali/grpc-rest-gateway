package rest_gateway_test

import com.improving.grpc_rest_gateway.runtime.server.GatewayServer
import rest_gateway_test.api.scala_api.{
  TestServiceAGatewayHandler,
  TestServiceBGatewayHandler,
  TestServiceDGatewayHandler,
  TestServiceEGatewayHandler
}
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.grpc.GrpcClientSettings
import rest_gateway_test.server.GrpcServer

import scala.io.StdIn

object Main {

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem[?] = ActorSystem[Nothing](Behaviors.empty, "grpc-rest-gateway-pekko")

    val host = "0.0.0.0"
    new GrpcServer(host, 8080).run()

    val settings = GrpcClientSettings.fromConfig("akka-gateway")
    GatewayServer(
      system.settings.config.getConfig("rest-gateway"),
      TestServiceAGatewayHandler(settings),
      TestServiceBGatewayHandler(settings),
      TestServiceDGatewayHandler(settings),
      TestServiceEGatewayHandler(settings)
    ).run()

    println("Server now online. \nPress RETURN to stop...")
    StdIn.readLine() // let it run until the user presses return
  }
}
