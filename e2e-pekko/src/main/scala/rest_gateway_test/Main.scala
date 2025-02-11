package rest_gateway_test

import com.improving.grpc_rest_gateway.runtime.server.GatewayServer
import rest_gateway_test.api.scala_api.{
  TestServiceAGatewayHandler,
  TestServiceBGatewayHandler,
  TestServiceDGatewayHandler,
  TestServiceEGatewayHandler
}
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.grpc.GrpcClientSettings
import rest_gateway_test.server.GrpcServer

import scala.io.StdIn

object Main {

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem[?] = ActorSystem[Nothing](Behaviors.empty, "grpc-rest-gateway-pekko")

    val host = "0.0.0.0"
    new GrpcServer(host, 8080).run()

    val settings = GrpcClientSettings.fromConfig("pekko-gateway")
    GatewayServer(
      "0.0.0.0",
      7070,
      TestServiceAGatewayHandler(settings),
      TestServiceBGatewayHandler(settings),
      TestServiceDGatewayHandler(settings),
      TestServiceEGatewayHandler(settings)
    ).run()

    println("Server now online. \nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
  }
}
