package rest_gateway_test

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.google.rpc.Code
import io.grpc.StatusRuntimeException
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import rest_gateway_test.api.model.common.{GetMessageRequest, TestRequestA, TestRequestB}
import rest_gateway_test.api.scala_api.TestServiceA.TestServiceAGrpc
import rest_gateway_test.api.scala_api.TestServiceB.TestServiceBGrpc
import rest_gateway_test.server.GrpcServer

import java.util.concurrent.{ExecutorService, Executors}
import scala.util.{Failure, Random, Success, Try}

class GrpcRestGatewayTest extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  private val Threads = 4
  private val grpcPort = new Random().nextInt(1000) + 3000
  private val gatewayPort = new Random().nextInt(1000) + 4000
  private lazy val grpcChannel = GrpcServer.getGrpcClient(grpcPort)
  private lazy val serviceAStub = TestServiceAGrpc.blockingStub(grpcChannel)
  private lazy val serviceBStub = TestServiceBGrpc.blockingStub(grpcChannel)
  private lazy val restClient = new RestGatewayClient(gatewayPort)

  private val gatewayServer =
    GrpcServer.startGateWayServer(gatewayServerExecutorSvc, grpcPort = grpcPort, gatewayPort = gatewayPort)
  private val grpcServer = GrpcServer.startGrpcServer(grpcServerExecutorSvc, port = grpcPort, blockUntilShutdown = false)

  override protected def afterAll(): Unit = {
    super.afterAll()
    gatewayServer.stop()
    grpcServer.stop()
  }

  "GrpcRestGateway" should {
    "simple GET call to service B" in {
      val requestId = 1L
      val grpcResponse = serviceBStub.process(TestRequestB(requestId))
      val restResponse = restClient.getRequestServiceB(requestId)
      restResponse shouldBe grpcResponse
    }

    "test status 404 (NOT_FOUND)" in {
      val requestId = 1L
      val message = s"RequestId {$requestId} not found"

      // GRPC call
      Try(serviceAStub.getRequest(TestRequestA(requestId))) match {
        case Failure(ex: StatusRuntimeException) =>
          ex.getMessage shouldBe s"NOT_FOUND: $message"
          val status = ex.getStatus
          status.getCode.value() shouldBe Code.NOT_FOUND.getNumber
          status.getDescription shouldBe message

        case Failure(ex) => fail(s"Test failed due to exception: \"${ex.getMessage}\".")
        case Success(_)  => fail("Test failed")
      }

      // rest call
      Try(restClient.getRequestServiceA(requestId)) match {
        case Failure(ex: HttpResponseException) =>
          ex.status shouldBe 404
          ex.message shouldBe message
        case Failure(ex) => fail(s"Test failed due to exception: \"${ex.getMessage}\".")
        case Success(_)  => fail("Test failed")
      }
    }

    "test status 400 (BAD_REQUEST)" in {
      val requestId = -1L
      val message = s"RequestId {$requestId} is not valid"

      // GRPC call
      Try(serviceAStub.getRequest(TestRequestA(requestId))) match {
        case Failure(ex: StatusRuntimeException) =>
          ex.getMessage shouldBe s"INVALID_ARGUMENT: $message"
          val status = ex.getStatus
          status.getCode.value() shouldBe Code.INVALID_ARGUMENT.getNumber
          status.getDescription shouldBe message

        case Failure(ex) => fail(s"Test failed due to exception: \"${ex.getMessage}\".")
        case Success(_)  => fail("Test failed")
      }

      // rest call
      Try(restClient.getRequestServiceA(requestId)) match {
        case Failure(ex: HttpResponseException) =>
          ex.status shouldBe 400
          ex.message shouldBe message
        case Failure(ex) => fail(s"Test failed due to exception: \"${ex.getMessage}\".")
        case Success(_)  => fail("Test failed")
      }
    }

    "post with GRPC and get with rest" in {
      val requestId = 1L
      val grpcResponse = serviceAStub.process(TestRequestA(requestId))
      val restResponse = restClient.getRequestServiceA(requestId)
      grpcResponse shouldBe restResponse
    }

    "get with rest with path parameter" in {
      val requestId = 1L
      val grpcResponse = serviceAStub.process(TestRequestA(requestId))
      val restResponse = restClient.getRequestServiceA(requestId, useRequestParam = false)
      grpcResponse shouldBe restResponse
    }

    "getMessageV1" in {
      val request = GetMessageRequest(messageId = 1, userId = "abc123")
      serviceAStub.getMessageV1(request) shouldBe restClient.getMessageV1(request)
    }

    "getMessageV2" in {
      val request = GetMessageRequest(messageId = 2, userId = "123abc")
      serviceAStub.getMessageV2(request) shouldBe restClient.getMessageV2(request)
    }

    "getMessageV3" in {
      val request = GetMessageRequest(messageId = 3, userId = "a1b2c3")
      serviceAStub.getMessageV3(request) shouldBe restClient.getMessageV3(request)
    }
  }

  private def grpcServerExecutorSvc: ExecutorService = executorSvc("grpc-server-%d")

  private def gatewayServerExecutorSvc: ExecutorService = executorSvc("grpc-rest-gateway-%d")

  private def executorSvc(format: String): ExecutorService =
    Executors.newFixedThreadPool(Threads, new ThreadFactoryBuilder().setNameFormat(format).build)
}
