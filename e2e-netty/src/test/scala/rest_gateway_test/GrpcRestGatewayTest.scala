package rest_gateway_test

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.google.rpc.Code
import io.grpc.StatusRuntimeException
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec
import rest_gateway_test.api.model.Color.{BLUE, GREEN, RED}
import rest_gateway_test.api.model.{
  Color,
  GetMessageRequest,
  GetMessageRequestV2,
  GetMessageRequestV3,
  GetMessageRequestV4,
  GetMessageRequestV5,
  GetMessageResponse,
  GetMessageResponseV5,
  GetMessageResponseV6,
  TestRequestA,
  TestRequestB,
  TestResponseA,
  TestResponseB
}
import rest_gateway_test.api.scala_api.TestServiceAGrpc
import rest_gateway_test.api.scala_api.TestServiceBGrpc
import rest_gateway_test.server.GrpcServer

import java.util.concurrent.{ExecutorService, Executors}
import scala.util.Random

class GrpcRestGatewayTest extends AnyWordSpec with Matchers with BeforeAndAfterAll with ScalaFutures {

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(
    timeout = Span(60, Seconds),
    interval = Span(100, Millis)
  )

  private val Threads = 4
  private val grpcPort = new Random().nextInt(1000) + 3000
  private val gatewayPort = new Random().nextInt(1000) + 4000
  private lazy val grpcChannel = GrpcServer.getGrpcClient(grpcPort)
  private lazy val serviceAStub = TestServiceAGrpc.stub(grpcChannel)
  private lazy val serviceBStub = TestServiceBGrpc.stub(grpcChannel)
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
      val grpcResponse = serviceBStub.process(TestRequestB(requestId)).futureValue
      val restResponse =
        restClient.getRequestServiceB[TestResponseB](requestId).futureValue.getOrElse(TestResponseB.defaultInstance)
      restResponse shouldBe grpcResponse
    }

    "simple PUT call to service B" in {
      val requestId = 2L
      val actualCode = restClient.updateServiceB[TestRequestB](TestRequestB(requestId)).futureValue
      actualCode shouldBe 204
    }

    "test status 404 (NOT_FOUND)" in {
      val requestId = 1L
      val message = s"RequestId {$requestId} not found"

      // GRPC call
      serviceAStub.getRequest(TestRequestA(requestId)).failed.futureValue match {
        case ex: StatusRuntimeException =>
          ex.getMessage shouldBe s"NOT_FOUND: $message"
          val status = ex.getStatus
          status.getCode.value() shouldBe Code.NOT_FOUND.getNumber
          status.getDescription shouldBe message

        case ex => fail(s"""Test failed due to exception: "${ex.getMessage}".""")
      }

      // rest call
      restClient.getRequestServiceA[TestResponseA](requestId).failed.futureValue match {
        case ex: HttpResponseException =>
          ex.status shouldBe 404
          ex.message shouldBe message

        case ex => fail(s"""Test failed due to exception: "${ex.getMessage}".""")
      }
    }

    "test status 400 (BAD_REQUEST)" in {
      val requestId = -1L
      val message = s"RequestId {$requestId} is not valid"

      // GRPC call
      serviceAStub.getRequest(TestRequestA(requestId)).failed.futureValue match {
        case ex: StatusRuntimeException =>
          ex.getMessage shouldBe s"INVALID_ARGUMENT: $message"
          val status = ex.getStatus
          status.getCode.value() shouldBe Code.INVALID_ARGUMENT.getNumber
          status.getDescription shouldBe message

        case ex => fail(s"""Test failed due to exception: "${ex.getMessage}".""")
      }

      // rest call
      restClient.getRequestServiceA[TestResponseA](requestId).failed.futureValue match {
        case ex: HttpResponseException =>
          ex.status shouldBe 400
          ex.message shouldBe message

        case ex => fail(s"""Test failed due to exception: "${ex.getMessage}".""")
      }
    }

    "post with GRPC and get with rest" in {
      val requestId = 1L
      val grpcResponse = serviceAStub.process(TestRequestA(requestId)).futureValue
      val restResponse =
        restClient.getRequestServiceA[TestResponseA](requestId).futureValue.getOrElse(TestResponseA.defaultInstance)
      grpcResponse shouldBe restResponse
    }

    "get with rest with path parameter" in {
      val requestId = 1L
      val grpcResponse = serviceAStub.process(TestRequestA(requestId)).futureValue
      val restResponse = restClient
        .getRequestServiceA[TestResponseA](requestId, useRequestParam = false)
        .futureValue
        .getOrElse(TestResponseA.defaultInstance)
      grpcResponse shouldBe restResponse
    }

    "getMessageV1" in {
      val request = GetMessageRequest(messageId = 1, userId = "abc123")
      serviceAStub.getMessageV1(request).futureValue shouldBe
        restClient
          .getMessageV1[GetMessageResponse](request.messageId, request.userId)
          .futureValue
          .getOrElse(GetMessageResponse.defaultInstance)
    }

    "getMessageV2" in {
      val request = GetMessageRequest(messageId = 2, userId = "123abc")
      serviceAStub.getMessageV2(request).futureValue shouldBe
        restClient
          .getMessageV2[GetMessageResponse](request.userId, request.messageId)
          .futureValue
          .getOrElse(GetMessageResponse.defaultInstance)
    }

    "getMessageV2AdditionalBinding" in {
      val request = GetMessageRequest(messageId = 3, userId = "a1b2c3")
      serviceAStub.getMessageV2(request).futureValue shouldBe
        restClient
          .getMessageV2AdditionalBinding[GetMessageResponse](request.userId, request.messageId)
          .futureValue
          .getOrElse(GetMessageResponse.defaultInstance)
    }

    "getMessageV3 with no sub field" in {
      val request = GetMessageRequestV2(messageId = 3, userId = "a1b2c3", sub = None)
      serviceAStub.getMessageV3(request).futureValue shouldBe
        restClient
          .getMessageV3[GetMessageResponse](request.messageId, request.userId)
          .futureValue
          .getOrElse(GetMessageResponse.defaultInstance)
    }

    "getMessageV3 with sub field" in {
      val request = GetMessageRequestV2(
        messageId = 16,
        userId = "super_user",
        sub = Some(GetMessageRequestV2.SubMessage(subField1 = 5.3, subField2 = 2.9f))
      )
      serviceAStub.getMessageV3(request).futureValue shouldBe
        restClient
          .getMessageV3[GetMessageResponse](
            request.messageId,
            request.userId,
            request.sub.map(_.subField1),
            request.sub.map(_.subField2)
          )
          .futureValue
          .getOrElse(GetMessageResponse.defaultInstance)
    }

    "getMessageV3 with sub field1 as path parameter" in {
      val request = GetMessageRequestV2(
        messageId = 16,
        userId = "super_user",
        sub = Some(GetMessageRequestV2.SubMessage(subField1 = 5.3, subField2 = 2.9f))
      )
      serviceAStub.getMessageV3(request).futureValue shouldBe
        restClient
          .getMessageV3AdditionalBinding[GetMessageResponse](
            request.messageId,
            request.userId,
            request.sub.map(_.subField1).getOrElse(0.0),
            request.sub.map(_.subField2)
          )
          .futureValue
          .getOrElse(GetMessageResponse.defaultInstance)
    }

    "post message" in {
      val request = GetMessageRequestV2(
        messageId = 23,
        userId = "admin",
        sub = Some(GetMessageRequestV2.SubMessage(subField1 = 19.0, subField2 = 2.14f))
      )
      serviceAStub.postMessage(request).futureValue shouldBe
        restClient
          .postMessage[GetMessageRequestV2.SubMessage, GetMessageResponse](
            request.userId,
            request.messageId,
            request.sub.get
          )
          .futureValue
          .getOrElse(GetMessageResponse.defaultInstance)
    }

    "put message" in {
      val request = GetMessageRequestV2(
        messageId = 30,
        userId = "admin",
        sub = Some(GetMessageRequestV2.SubMessage(subField1 = 2.0, subField2 = 2.14f))
      )
      serviceAStub.putMessage(request).futureValue shouldBe
        restClient
          .putMessage[GetMessageRequestV2.SubMessage, GetMessageResponse](
            request.userId,
            request.messageId,
            request.sub.get
          )
          .futureValue
          .getOrElse(GetMessageResponse.defaultInstance)
    }

    "GetMessageV4" in {
      val request = GetMessageRequestV3(messageId = 0 to 5, color = RED)
      serviceAStub.getMessageV4(request).futureValue shouldBe
        restClient
          .getMessageV4[GetMessageResponse](request.messageId, request.color.name)
          .futureValue
          .getOrElse(GetMessageResponse.defaultInstance)
    }

    "GetMessageV4 with no color" in {
      val request = GetMessageRequestV3(messageId = 0 to 3, color = Color.Unrecognized(50))
      serviceAStub.getMessageV4(request).futureValue shouldBe
        restClient
          .getMessageV4[GetMessageResponse](request.messageId, request.color.name)
          .futureValue
          .getOrElse(GetMessageResponse.defaultInstance)
    }

    "GetMessageV4 no message id" in {
      val request = GetMessageRequestV3(messageId = Seq.empty, color = BLUE)
      serviceAStub.getMessageV4(request).futureValue shouldBe
        restClient
          .getMessageV4[GetMessageResponse](request.messageId, request.color.name)
          .futureValue
          .getOrElse(GetMessageResponse.defaultInstance)
    }

    "GetMessageV5" in {
      val request = GetMessageRequestV4(
        colors = Seq(RED, GREEN, BLUE),
        doubles = (0 to 3).map(_.toDouble),
        floats = (0 to 4).map(_.toFloat),
        longs = (0 to 2).map(_.toLong),
        booleans = Seq(true, false, true)
      )
      serviceAStub.getMessageV5(request).futureValue shouldBe
        restClient
          .getMessageV5[GetMessageResponse](
            request.colors.map(_.name),
            request.doubles,
            request.floats,
            request.longs,
            request.booleans
          )
          .futureValue
          .getOrElse(GetMessageResponse.defaultInstance)
    }

    "ProcessMessageV5" in {
      testProcessMessageV5(Some(5))
      testProcessMessageV5(Some(-23))
      testProcessMessageV5(Some(0))
      testProcessMessageV5(None)
    }

    "processMessageV6" in {
      val request = GetMessageRequest(messageId = 1000, userId = "mnbvggg")
      serviceAStub.processMessageV6(request).futureValue shouldBe
        restClient.processMessageV6[GetMessageResponseV6](request.messageId, request.userId).futureValue.get
    }
  }

  private def testProcessMessageV5(value: Option[Int]) = {
    val request = GetMessageRequestV5(value)
    serviceAStub.processMessageV5(request).futureValue shouldBe
      restClient
        .processMessageV5[GetMessageResponseV5](value)
        .futureValue
        .get
  }

  private def grpcServerExecutorSvc: ExecutorService = executorSvc("grpc-server-%d")

  private def gatewayServerExecutorSvc: ExecutorService = executorSvc("grpc-rest-gateway-%d")

  private def executorSvc(format: String): ExecutorService =
    Executors.newFixedThreadPool(Threads, new ThreadFactoryBuilder().setNameFormat(format).build)
}
