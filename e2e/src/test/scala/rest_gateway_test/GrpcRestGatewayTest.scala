package rest_gateway_test

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.google.rpc.Code
import io.grpc.StatusRuntimeException
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import rest_gateway_test.api.model.common.{TestRequestA, TestRequestB, TestResponseA, TestResponseB}
import rest_gateway_test.api.scala_api.TestServiceA.TestServiceAGrpc
import rest_gateway_test.api.scala_api.TestServiceB.TestServiceBGrpc
import rest_gateway_test.server.GrpcServer

import java.util.concurrent.{ExecutorService, Executors}
import scala.util.{Failure, Success, Try}

class GrpcRestGatewayTest extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  private val Threads = 4
  private lazy val grpcChannel = GrpcServer.getGrpcClient
  private lazy val serviceAStub = TestServiceAGrpc.blockingStub(grpcChannel)
  private lazy val serviceBStub = TestServiceBGrpc.blockingStub(grpcChannel)
  private lazy val restClient = new RestGatewayClient

  private val gatewayServer = GrpcServer.startGateWayServer(gatewayServerExecutorSvc)
  private val grpcServer = GrpcServer.startGrpcServer(grpcServerExecutorSvc, blockUntilShutdown = false)

  override protected def afterAll(): Unit = {
    super.afterAll()
    gatewayServer.stop()
    grpcServer.stop()
  }

  "GrpcRestGateway" should {
    "make simple GRPC call to service B" in {
      val requestId = 1L
      val response = serviceBStub.process(TestRequestB(requestId))
      response shouldBe TestResponseB(success = true, requestId = requestId, result = s"RequestId: $requestId")
    }

    "make simple REST call to service B" in {
      val requestId = 1L
      val actual = restClient.postProcessServiceB(requestId)
      actual shouldBe TestResponseB(success = true, requestId = requestId, result = s"RequestId: $requestId")
    }

    "test status 404" should {
      val requestId = 1L
      val message = s"RequestId {$requestId} not found"
      "make GRPC call to service A with non-existing request id" in {
        Try(serviceAStub.getRequest(TestRequestA(requestId))) match {
          case Failure(ex: StatusRuntimeException) =>
            ex.getMessage shouldBe s"NOT_FOUND: $message"
            val status = ex.getStatus
            status.getCode.value() shouldBe Code.NOT_FOUND.getNumber
            status.getDescription shouldBe message

          case Failure(ex) => fail(ex.getMessage)
          case Success(_)  => fail("Test failed")
        }
      }

      "make REST call to service A with non-existing request id" in {
        Try(restClient.getRequestServiceA(requestId)) match {
          case Failure(ex: HttpResponseException) =>
            ex.status shouldBe 404
            ex.message shouldBe message
          case Failure(ex) => fail(s"Test failed due to exception: \"${ex.getMessage}\".")
          case Success(_)  => fail("Test failed")
        }
      }
    }

  }

  private def grpcServerExecutorSvc: ExecutorService = executorSvc("grpc-server-%d")

  private def gatewayServerExecutorSvc: ExecutorService = executorSvc("grpc-rest-gateway-%d")

  private def executorSvc(format: String): ExecutorService =
    Executors.newFixedThreadPool(Threads, new ThreadFactoryBuilder().setNameFormat(format).build)
}
