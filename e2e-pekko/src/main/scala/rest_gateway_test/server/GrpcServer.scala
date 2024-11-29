package rest_gateway_test
package server

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.grpc.scaladsl.{ServerReflection, ServiceHandler}
import org.apache.pekko.http.scaladsl.Http
import rest_gateway_test.api.scala_api.{TestServiceA, TestServiceAHandler, TestServiceBHandler}
import rest_gateway_test.service.{TestServiceAImpl, TestServiceBImpl}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

class GrpcServer(host: String, port: Int)(implicit system: ActorSystem[?]) {

  private implicit val ec: ExecutionContext = system.executionContext

  def run(): Future[Http.ServerBinding] = {
    val services = ServiceHandler.concatOrNotFound(
      TestServiceAHandler.partial(new TestServiceAImpl()),
      TestServiceBHandler.partial(new TestServiceBImpl()),
      ServerReflection.partial(List(TestServiceA, TestServiceA))
    )
    val bound: Future[Http.ServerBinding] =
      Http()
        .newServerAt(host, port)
        .bind(services)
        .map(
          _.addToCoordinatedShutdown(hardTerminationDeadline = 10.seconds)
        )

    bound.onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        system
          .log
          .info(
            s"gRPC server bound to {}:{}",
            address.getHostString,
            address.getPort
          )
      case Failure(ex) => system.log.error("Failed to bind gRPC endpoint, terminating system", ex)
    }
    bound
  }
}
