package rest_gateway_test

import rest_gateway_test.api.model.common.{TestRequestB, TestResponseA, TestResponseB}
import sttp.client3._
import scalapb.json4s.JsonFormat

class RestGatewayClient(gatewayPort: Int) {

  private val baseUrl = s"http://localhost:$gatewayPort"
  private val basePath = "restgateway/test"
  private val serviceAUri = s"$baseUrl/$basePath/testservicea"
  private val serviceBUri = s"$baseUrl/$basePath/testserviceb"

  private val client = SimpleHttpClient()

  def getRequestServiceA(requestId: Long): TestResponseA = {
    val response = client.send(basicRequest.get(uri"$serviceAUri?request_id=$requestId").response(asString))
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

}

final case class HttpResponseException(status: Int, message: String) extends RuntimeException(message)
