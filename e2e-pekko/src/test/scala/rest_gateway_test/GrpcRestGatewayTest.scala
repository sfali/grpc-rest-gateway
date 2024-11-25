package rest_gateway_test

import com.google.rpc.Code
import com.improving.grpc_rest_gateway.runtime.server.HttpServer
import io.grpc.StatusRuntimeException
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.grpc.GrpcClientSettings
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
  GetMessageResponse,
  TestRequestA,
  TestRequestB,
  TestResponseA,
  TestResponseB
}
import rest_gateway_test.api.scala_api.{TestServiceAClient, TestServiceBClient, TestServiceBGatewayHandler}
import rest_gateway_test.server.GrpcServer
import rest_gateway_test.test.TestServiceAGatewayHandler

import scala.util.{Failure, Random, Success, Try}

class GrpcRestGatewayTest extends AnyWordSpec with Matchers with BeforeAndAfterAll with ScalaFutures {

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(
    timeout = Span(60, Seconds),
    interval = Span(100, Millis)
  )
  private implicit val system: ActorSystem[Nothing] = ActorSystem[Nothing](Behaviors.empty, "pekko-test")
  private val grpcPort = new Random().nextInt(1000) + 3000
  private val gatewayPort = new Random().nextInt(1000) + 4000
  private val settings = GrpcClientSettings.connectToServiceAt("localhost", grpcPort).withTls(false)
  private lazy val serviceAClient = TestServiceAClient(settings)
  private lazy val serviceBClient = TestServiceBClient(settings)
  private lazy val restClient = new RestGatewayClient(gatewayPort)

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    new GrpcServer("localhost", grpcPort).run()

    new HttpServer().run(
      "localhost",
      gatewayPort,
      TestServiceAGatewayHandler(settings),
      TestServiceBGatewayHandler(settings)
    )
  }

  "GrpcRestGateway" should {
    "simple GET call to service B" in {
      val requestId = 1L
      val grpcResponse = serviceBClient.process(TestRequestB(requestId)).futureValue
      val restResponse = restClient.getRequestServiceB[TestResponseB](requestId).getOrElse(TestResponseB.defaultInstance)
      restResponse shouldBe grpcResponse
    }

    "test status 404 (NOT_FOUND)" in {
      val requestId = 1L
      val message = s"RequestId {$requestId} not found"

      // GRPC call
      serviceAClient.getRequest(TestRequestA(requestId)).failed.futureValue match {
        case ex: StatusRuntimeException =>
          ex.getMessage shouldBe s"NOT_FOUND: $message"
          val status = ex.getStatus
          status.getCode.value() shouldBe Code.NOT_FOUND.getNumber
          status.getDescription shouldBe message

        case ex => fail(s"""Test failed due to exception: "${ex.getMessage}".""")
      }

      // rest call
      Try(restClient.getRequestServiceA[TestResponseA](requestId)) match {
        case Failure(ex: HttpResponseException) =>
          ex.status shouldBe 404
          ex.message shouldBe message
        case Failure(ex) => fail(s""""Test failed due to exception: "${ex.getMessage}".""")
        case Success(_)  => fail("Test failed")
      }
    }

    "test status 400 (BAD_REQUEST)" in {
      val requestId = -1L
      val message = s"RequestId {$requestId} is not valid"

      // GRPC call
      serviceAClient.getRequest(TestRequestA(requestId)).failed.futureValue match {
        case ex: StatusRuntimeException =>
          ex.getMessage shouldBe s"INVALID_ARGUMENT: $message"
          val status = ex.getStatus
          status.getCode.value() shouldBe Code.INVALID_ARGUMENT.getNumber
          status.getDescription shouldBe message

        case ex => fail(s"""Test failed due to exception: "${ex.getMessage}".""")
      }

      // rest call
      Try(restClient.getRequestServiceA[TestResponseA](requestId)) match {
        case Failure(ex: HttpResponseException) =>
          ex.status shouldBe 400
          ex.message shouldBe message
        case Failure(ex) => fail(s"""Test failed due to exception: "${ex.getMessage}".""")
        case Success(_)  => fail("Test failed")
      }
    }

    "post with GRPC and get with rest" in {
      val requestId = 1L
      val grpcResponse = serviceAClient.process(TestRequestA(requestId)).futureValue
      val restResponse = restClient.getRequestServiceA[TestResponseA](requestId).getOrElse(TestResponseA.defaultInstance)
      grpcResponse shouldBe restResponse
    }

    "getMessageV1" in {
      val request = GetMessageRequest(messageId = 1, userId = "abc123")
      serviceAClient.getMessageV1(request).futureValue shouldBe
        restClient
          .getMessageV1[GetMessageResponse](request.messageId, request.userId)
          .getOrElse(GetMessageResponse.defaultInstance)
    }

    "getMessageV2" in {
      val request = GetMessageRequest(messageId = 2, userId = "123abc")
      serviceAClient.getMessageV2(request).futureValue shouldBe
        restClient
          .getMessageV2[GetMessageResponse](request.userId, request.messageId)
          .getOrElse(GetMessageResponse.defaultInstance)
    }

    "getMessageV2AdditionalBinding" in {
      val request = GetMessageRequest(messageId = 3, userId = "a1b2c3")
      serviceAClient.getMessageV2(request).futureValue shouldBe
        restClient
          .getMessageV2AdditionalBinding[GetMessageResponse](request.userId, request.messageId)
          .getOrElse(GetMessageResponse.defaultInstance)
    }

    "getMessageV3 with no sub field" in {
      val request = GetMessageRequestV2(messageId = 3, userId = "a1b2c3", sub = None)
      serviceAClient.getMessageV3(request).futureValue shouldBe
        restClient
          .getMessageV3[GetMessageResponse](request.messageId, request.userId)
          .getOrElse(GetMessageResponse.defaultInstance)
    }

    "getMessageV3 with sub field" in {
      val request = GetMessageRequestV2(
        messageId = 16,
        userId = "super_user",
        sub = Some(GetMessageRequestV2.SubMessage(subField1 = 5.3, subField2 = 2.9f))
      )
      serviceAClient.getMessageV3(request).futureValue shouldBe
        restClient
          .getMessageV3[GetMessageResponse](
            request.messageId,
            request.userId,
            request.sub.map(_.subField1),
            request.sub.map(_.subField2)
          )
          .getOrElse(GetMessageResponse.defaultInstance)
    }

    "post message" in {
      val request = GetMessageRequestV2(
        messageId = 23,
        userId = "admin",
        sub = Some(GetMessageRequestV2.SubMessage(subField1 = 19.0, subField2 = 2.14f))
      )
      serviceAClient.postMessage(request).futureValue shouldBe
        restClient
          .postMessage[GetMessageRequestV2.SubMessage, GetMessageResponse](
            request.userId,
            request.messageId,
            request.sub.get
          )
          .getOrElse(GetMessageResponse.defaultInstance)
    }

    "put message" in {
      val request = GetMessageRequestV2(
        messageId = 30,
        userId = "admin",
        sub = Some(GetMessageRequestV2.SubMessage(subField1 = 2.0, subField2 = 2.14f))
      )
      serviceAClient.putMessage(request).futureValue shouldBe
        restClient
          .putMessage[GetMessageRequestV2.SubMessage, GetMessageResponse](
            request.userId,
            request.messageId,
            request.sub.get
          )
          .getOrElse(GetMessageResponse.defaultInstance)
    }

    "GetMessageV4" in {
      val request = GetMessageRequestV3(messageId = 0 to 5, color = RED)
      serviceAClient.getMessageV4(request).futureValue shouldBe
        restClient
          .getMessageV4[GetMessageResponse](request.messageId, request.color.name)
          .getOrElse(GetMessageResponse.defaultInstance)
    }

    "GetMessageV4 with no color" in {
      val request = GetMessageRequestV3(messageId = 0 to 3, color = Color.Unrecognized(50))
      serviceAClient.getMessageV4(request).futureValue shouldBe
        restClient
          .getMessageV4[GetMessageResponse](request.messageId, request.color.name)
          .getOrElse(GetMessageResponse.defaultInstance)
    }

    "GetMessageV4 no message id" in {
      val request = GetMessageRequestV3(messageId = Seq.empty, color = BLUE)
      serviceAClient.getMessageV4(request).futureValue shouldBe
        restClient
          .getMessageV4[GetMessageResponse](request.messageId, request.color.name)
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
      serviceAClient.getMessageV5(request).futureValue shouldBe
        restClient
          .getMessageV5[GetMessageResponse](
            request.colors.map(_.name),
            request.doubles,
            request.floats,
            request.longs,
            request.booleans
          )
          .getOrElse(GetMessageResponse.defaultInstance)
    }
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    system.terminate()
  }
}
