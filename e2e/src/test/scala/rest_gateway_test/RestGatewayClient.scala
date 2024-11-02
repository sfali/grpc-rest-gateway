package rest_gateway_test

import rest_gateway_test.api.model.common.{TestRequestB, TestResponseB}
import rest_gateway_test.server.GrpcServer
import sttp.client3._
import scalapb.json4s.JsonFormat

class RestGatewayClient {

  private val baseUrl = s"http://localhost:${GrpcServer.GatewayPort}"
  private val basePath = "restgateway/test"
  private val serviceBUri = uri"$baseUrl/$basePath/testserviceb"

  private val client = SimpleHttpClient()

  def postProcessServiceB(requestId: Long): TestResponseB =
    client
      .send(
        basicRequest
          .post(serviceBUri)
          .body(JsonFormat.toJsonString(TestRequestB(requestId)))
          .response(asString)
      )
      .body match {
      case Left(ex)    => throw new RuntimeException(ex)
      case Right(body) => JsonFormat.fromJsonString[TestResponseB](body)
    }

}
