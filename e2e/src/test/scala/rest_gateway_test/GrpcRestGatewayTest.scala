package rest_gateway_test

import com.google.common.util.concurrent.ThreadFactoryBuilder
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import rest_gateway_test.api.model.common.{TestRequestB, TestResponseB}
import rest_gateway_test.api.scala_api.TestServiceA.TestServiceAGrpc
import rest_gateway_test.api.scala_api.TestServiceB.TestServiceBGrpc
import rest_gateway_test.server.GrpcServer

import java.util.concurrent.{ExecutorService, Executors}

class GrpcRestGatewayTest extends AnyWordSpec with Matchers {

  private val Threads = 4
  private lazy val grpcChannel = GrpcServer.getGrpcClient
  private lazy val serviceAStub = TestServiceAGrpc.blockingStub(grpcChannel)
  private lazy val serviceBStub = TestServiceBGrpc.blockingStub(grpcChannel)
  private lazy val restClient = new RestGatewayClient

  GrpcServer.startGateWayServer(gatewayServerExecutorSvc)
  GrpcServer.startGrpcServer(grpcServerExecutorSvc, blockUntilShutdown = false)

  "GrpcRestGateway" should {
    "Simple GRPC call to service B" in {
      val requestId = 1L
      val response = serviceBStub.process(TestRequestB(requestId))
      response shouldBe TestResponseB(success = true, requestId = requestId, result = s"RequestId: $requestId")
    }

    "Simple rest call to service B" in {
      val requestId = 1L
      val actual = restClient.postProcessServiceB(requestId)
      actual shouldBe TestResponseB(success = true, requestId = requestId, result = s"RequestId: $requestId")
    }
  }

  private def grpcServerExecutorSvc: ExecutorService = executorSvc("grpc-server-%d")

  private def gatewayServerExecutorSvc: ExecutorService = executorSvc("grpc-rest-gateway-%d")

  private def executorSvc(format: String): ExecutorService =
    Executors.newFixedThreadPool(Threads, new ThreadFactoryBuilder().setNameFormat(format).build)
}
