package rest_gateway_test

import com.improving.grpc_rest_gateway.runtime.core.*
import scalapb.json4s.JsonFormat
import scalapb.{GeneratedMessage, GeneratedMessageCompanion}
import sttp.client3.{SimpleHttpClient, UriContext, asString, basicRequest}
import sttp.model.Uri

import scala.util.Try

class RestGatewayClient(gatewayPort: Int) {

  private val baseUrl = s"http://localhost:$gatewayPort"
  private val basePath = "restgateway/test"
  private val serviceAUri = s"$baseUrl/$basePath/testservicea"
  private val serviceBUri = s"$baseUrl/$basePath/testserviceb"

  private val client = SimpleHttpClient()

  def getRequestServiceA[Response <: GeneratedMessage: GeneratedMessageCompanion](
    requestId: Long,
    useRequestParam: Boolean = true
  ): Try[Response] = {
    val uri = if (useRequestParam) uri"$serviceAUri?request_id=$requestId" else uri"$serviceAUri/$requestId"
    val response = client.send(basicRequest.get(uri).response(asString))
    response.body match {
      case Left(ex)    => throw HttpResponseException(response.code.code, ex)
      case Right(body) => parseBody(body)
    }
  }

  def getRequestServiceB[Response <: GeneratedMessage: GeneratedMessageCompanion](requestId: Long): Try[Response] = {
    val response = client.send(basicRequest.get(uri"$serviceBUri?requestId=$requestId").response(asString))
    response.body match {
      case Left(ex)    => throw HttpResponseException(response.code.code, ex)
      case Right(body) => parseBody(body)
    }
  }

  def postProcessServiceB[Request <: GeneratedMessage, Response <: GeneratedMessage: GeneratedMessageCompanion](
    body: Request
  ): Try[Response] =
    client
      .send(
        basicRequest
          .post(uri"$serviceBUri")
          .body(JsonFormat.toJsonString(body))
          .response(asString)
      )
      .body match {
      case Left(ex)    => throw new RuntimeException(ex)
      case Right(body) => parseBody(body)
    }

  def getMessageV1[Response <: GeneratedMessage: GeneratedMessageCompanion](
    messageId: Int,
    userId: String
  ): Try[Response] = getMessage(uri"$baseUrl/v1/test/messages/$messageId?user_id=$userId")

  def getMessageV2[Response <: GeneratedMessage: GeneratedMessageCompanion](
    userId: String,
    messageId: Int
  ): Try[Response] = getMessage(uri"$baseUrl/v1/test/users/$userId?message_id=$messageId")

  def getMessageV2AdditionalBinding[Response <: GeneratedMessage: GeneratedMessageCompanion](
    userId: String,
    messageId: Int
  ): Try[Response] = getMessage(uri"$baseUrl/v1/test/users/$userId/messages/$messageId")

  def getMessageV3[Response <: GeneratedMessage: GeneratedMessageCompanion](
    messageId: Int,
    userId: String,
    subField1: Option[Double] = None,
    subField2: Option[Float] = None
  ): Try[Response] = {
    var uri = uri"$baseUrl/v1/test/messages/$messageId/users/$userId"
    uri = (subField1, subField2) match {
      case (Some(f1), Some(f2)) => uri.withParams(("sub.sub_field1", f1.toString), ("sub.sub_field2", f2.toString))
      case (Some(f1), None)     => uri.withParams(("sub.sub_field1", f1.toString))
      case (None, Some(f2))     => uri.withParams(("sub.sub_field2", f2.toString))
      case _                    => uri
    }
    getMessage(uri)
  }

  def postMessage[Body <: GeneratedMessage, Response <: GeneratedMessage: GeneratedMessageCompanion](
    userId: String,
    messageId: Int,
    body: Body
  ): Try[Response] =
    client
      .send(
        basicRequest
          .post(uri"$baseUrl/v1/test/users/$userId/messages/$messageId")
          .body(JsonFormat.toJsonString(body))
          .response(asString)
      )
      .body match {
      case Left(ex)    => throw new RuntimeException(ex)
      case Right(body) => parseBody(body)
    }

  def putMessage[Body <: GeneratedMessage, Response <: GeneratedMessage: GeneratedMessageCompanion](
    userId: String,
    messageId: Int,
    body: Body
  ): Try[Response] =
    client
      .send(
        basicRequest
          .put(uri"$baseUrl/v1/test/users/$userId/messages/$messageId")
          .body(JsonFormat.toJsonString(body))
          .response(asString)
      )
      .body match {
      case Left(ex)    => throw new RuntimeException(ex)
      case Right(body) => parseBody(body)
    }

  def getMessageV4[Response <: GeneratedMessage: GeneratedMessageCompanion](
    messageIds: Seq[Int],
    color: String
  ): Try[Response] = {
    val params = messageIds.map(v => "message_id" -> v.toString) :+ ("color" -> color)
    val uri = uri"$baseUrl/v1/test/messages".withParams(params*)
    getMessage(uri)
  }

  def getMessageV5[Response <: GeneratedMessage: GeneratedMessageCompanion](
    colors: Seq[String],
    doubles: Seq[_root_.scala.Double],
    floats: Seq[_root_.scala.Float],
    longs: Seq[_root_.scala.Long],
    booleans: Seq[_root_.scala.Boolean]
  ): Try[Response] = {
    val params =
      colors.map(name => "colors" -> name) ++ doubles.map(v => "doubles" -> v.toString) ++
        floats.map(v => "floats" -> v.toString) ++ longs.map(v => "longs" -> v.toString) ++
        booleans.map(v => "booleans" -> v.toString)
    val uri = uri"$baseUrl/v1/test/array".withParams(params*)
    getMessage(uri)
  }

  private def getMessage[Response <: GeneratedMessage: GeneratedMessageCompanion](uri: Uri): Try[Response] = {
    val response = client.send(basicRequest.get(uri).response(asString))
    response.body match {
      case Left(ex)    => throw HttpResponseException(response.code.code, ex)
      case Right(body) => parseBody(body)
    }
  }
}

final case class HttpResponseException(status: Int, message: String) extends RuntimeException(message)
