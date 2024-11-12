package rest_gateway_test

import rest_gateway_test.api.model.{
  GetMessageRequest,
  GetMessageRequestV2,
  GetMessageResponse,
  TestRequestB,
  TestResponseA,
  TestResponseB
}
import sttp.client3._
import scalapb.json4s.JsonFormat
import sttp.model.Uri

class RestGatewayClient(gatewayPort: Int) {

  private val baseUrl = s"http://localhost:$gatewayPort"
  private val basePath = "restgateway/test"
  private val serviceAUri = s"$baseUrl/$basePath/testservicea"
  private val serviceBUri = s"$baseUrl/$basePath/testserviceb"

  private val client = SimpleHttpClient()

  def getRequestServiceA(requestId: Long, useRequestParam: Boolean = true): TestResponseA = {
    val uri = if (useRequestParam) uri"$serviceAUri?request_id=$requestId" else uri"$serviceAUri/$requestId"
    val response = client.send(basicRequest.get(uri).response(asString))
    response.body match {
      case Left(ex)    => throw HttpResponseException(response.code.code, ex)
      case Right(body) => JsonFormat.fromJsonString[TestResponseA](body)
    }
  }

  def getRequestServiceB(requestId: Long): TestResponseB = {
    val response = client.send(basicRequest.get(uri"$serviceBUri?requestId=$requestId").response(asString))
    response.body match {
      case Left(ex)    => throw HttpResponseException(response.code.code, ex)
      case Right(body) => JsonFormat.fromJsonString[TestResponseB](body)
    }
  }

  def postProcessServiceB(requestId: Long): TestResponseB =
    client
      .send(
        basicRequest
          .post(uri"$serviceBUri")
          .body(JsonFormat.toJsonString(TestRequestB(requestId)))
          .response(asString)
      )
      .body match {
      case Left(ex)    => throw new RuntimeException(ex)
      case Right(body) => JsonFormat.fromJsonString[TestResponseB](body)
    }

  def getMessageV1(request: GetMessageRequest): GetMessageResponse =
    getMessage(uri"$baseUrl/v1/test/messages/${request.messageId}?user_id=${request.userId}")

  def getMessageV2(request: GetMessageRequest): GetMessageResponse =
    getMessage(uri"$baseUrl/v1/test/users/${request.userId}?message_id=${request.messageId}")

  def getMessageV2AdditionalBinding(request: GetMessageRequest): GetMessageResponse =
    getMessage(uri"$baseUrl/v1/test/users/${request.userId}/messages/${request.messageId}")

  def getMessageV3(request: GetMessageRequestV2): GetMessageResponse = {
    var uri = uri"$baseUrl/v1/test/messages/${request.messageId}/users/${request.userId}"
    uri = request
      .sub
      .map(sub => uri.withParams(("sub.sub_field1", sub.subField1.toString), ("sub.sub_field2", sub.subField2.toString)))
      .getOrElse(uri)
    getMessage(uri)
  }

  def postMessage(request: GetMessageRequestV2): GetMessageResponse =
    client
      .send(
        basicRequest
          .post(uri"$baseUrl/v1/test/users/${request.userId}/messages/${request.messageId}")
          .body(JsonFormat.toJsonString(request.sub.get))
          .response(asString)
      )
      .body match {
      case Left(ex)    => throw new RuntimeException(ex)
      case Right(body) => JsonFormat.fromJsonString[GetMessageResponse](body)
    }

  def putMessage(request: GetMessageRequestV2): GetMessageResponse =
    client
      .send(
        basicRequest
          .put(uri"$baseUrl/v1/test/users/${request.userId}/messages/${request.messageId}")
          .body(JsonFormat.toJsonString(request.sub.get))
          .response(asString)
      )
      .body match {
      case Left(ex)    => throw new RuntimeException(ex)
      case Right(body) => JsonFormat.fromJsonString[GetMessageResponse](body)
    }

  private def getMessage(uri: Uri): GetMessageResponse = {
    val response = client.send(basicRequest.get(uri).response(asString))
    response.body match {
      case Left(ex)    => throw HttpResponseException(response.code.code, ex)
      case Right(body) => JsonFormat.fromJsonString[GetMessageResponse](body)
    }
  }

}

final case class HttpResponseException(status: Int, message: String) extends RuntimeException(message)
