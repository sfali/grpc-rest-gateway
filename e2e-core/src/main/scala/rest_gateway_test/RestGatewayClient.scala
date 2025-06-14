package rest_gateway_test

import com.improving.grpc_rest_gateway.runtime.core.*
import scalapb.json4s.JsonFormat
import scalapb.{GeneratedMessage, GeneratedMessageCompanion}
import sttp.client3.{HttpClientFutureBackend, UriContext, asString, basicRequest}
import sttp.model.Uri

import java.net.http.HttpClient
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

class RestGatewayClient(gatewayPort: Int) {

  private val baseUrl = s"http://localhost:$gatewayPort"
  private val basePath = "restgateway/test"
  private val serviceAUri = s"$baseUrl/$basePath/testservicea"
  private val serviceBUri = s"$baseUrl/$basePath/testserviceb"

  private val backend = HttpClientFutureBackend.usingClient(HttpClient.newHttpClient())

  def getRequestServiceA[Response <: GeneratedMessage: GeneratedMessageCompanion](
    requestId: Long,
    useRequestParam: Boolean = true
  ): Future[Try[Response]] = {
    val uri = if (useRequestParam) uri"$serviceAUri?request_id=$requestId" else uri"$serviceAUri/$requestId"
    basicRequest.get(uri).response(asString).send(backend).map { response =>
      response.body match {
        case Left(ex)    => throw HttpResponseException(response.code.code, ex)
        case Right(body) => parseBody(body)
      }
    }
  }

  def getRequestServiceB[Response <: GeneratedMessage: GeneratedMessageCompanion](
    requestId: Long
  ): Future[Try[Response]] =
    basicRequest
      .get(uri"$serviceBUri?requestId=$requestId")
      .response(asString)
      .send(backend)
      .map { response =>
        response.body match {
          case Left(ex)    => throw HttpResponseException(response.code.code, ex)
          case Right(body) => parseBody(body)
        }
      }

  def postProcessServiceB[Request <: GeneratedMessage, Response <: GeneratedMessage: GeneratedMessageCompanion](
    body: Request
  ): Future[Try[Response]] =
    basicRequest.post(uri"$serviceBUri").body(JsonFormat.toJsonString(body)).response(asString).send(backend).map {
      response =>
        response.body match {
          case Left(ex)    => throw HttpResponseException(response.code.code, ex)
          case Right(body) => parseBody(body)
        }
    }

  def updateServiceB[Request <: GeneratedMessage](
    body: Request
  ): Future[Int] =
    basicRequest
      .put(uri"$serviceBUri/update")
      .body(JsonFormat.toJsonString(body))
      .response(asString)
      .send(backend)
      .map(_.code.code)

  def getMessageV1[Response <: GeneratedMessage: GeneratedMessageCompanion](
    messageId: Int,
    userId: String
  ): Future[Try[Response]] = getMessage(uri"$baseUrl/v1/test/messages/$messageId?user_id=$userId")

  def getMessageV2[Response <: GeneratedMessage: GeneratedMessageCompanion](
    userId: String,
    messageId: Int
  ): Future[Try[Response]] = getMessage(uri"$baseUrl/v1/test/users/$userId?message_id=$messageId")

  def getMessageV2AdditionalBinding[Response <: GeneratedMessage: GeneratedMessageCompanion](
    userId: String,
    messageId: Int
  ): Future[Try[Response]] = getMessage(uri"$baseUrl/v1/test/users/$userId/messages/$messageId")

  def getMessageV3[Response <: GeneratedMessage: GeneratedMessageCompanion](
    messageId: Int,
    userId: String,
    subField1: Option[Double] = None,
    subField2: Option[Float] = None
  ): Future[Try[Response]] = {
    var uri = uri"$baseUrl/v1/test/messages/$messageId/users/$userId"
    uri = (subField1, subField2) match {
      case (Some(f1), Some(f2)) => uri.withParams(("sub.sub_field1", f1.toString), ("sub.sub_field2", f2.toString))
      case (Some(f1), None)     => uri.withParams(("sub.sub_field1", f1.toString))
      case (None, Some(f2))     => uri.withParams(("sub.sub_field2", f2.toString))
      case _                    => uri
    }
    getMessage(uri)
  }

  def getMessageV3AdditionalBinding[Response <: GeneratedMessage: GeneratedMessageCompanion](
    messageId: Int,
    userId: String,
    subField1: Double,
    subField2: Option[Float] = None
  ): Future[Try[Response]] = {
    var uri = uri"$baseUrl/v1/test/messages/$messageId/users/$userId/sub/$subField1"
    uri = subField2 match {
      case Some(f2) => uri.withParams(("sub.sub_field2", f2.toString))
      case _        => uri
    }
    getMessage(uri)
  }

  def postMessage[Body <: GeneratedMessage, Response <: GeneratedMessage: GeneratedMessageCompanion](
    userId: String,
    messageId: Int,
    body: Body
  ): Future[Try[Response]] =
    basicRequest
      .post(uri"$baseUrl/v1/test/users/$userId/messages/$messageId")
      .body(JsonFormat.toJsonString(body))
      .response(asString)
      .send(backend)
      .map { response =>
        response.body match {
          case Left(ex)    => throw HttpResponseException(response.code.code, ex)
          case Right(body) => parseBody(body)
        }
      }

  def putMessage[Body <: GeneratedMessage, Response <: GeneratedMessage: GeneratedMessageCompanion](
    userId: String,
    messageId: Int,
    body: Body
  ): Future[Try[Response]] =
    basicRequest
      .put(uri"$baseUrl/v1/test/users/$userId/messages/$messageId")
      .body(JsonFormat.toJsonString(body))
      .response(asString)
      .send(backend)
      .map { response =>
        response.body match {
          case Left(ex)    => throw HttpResponseException(response.code.code, ex)
          case Right(body) => parseBody(body)
        }
      }

  def getMessageV4[Response <: GeneratedMessage: GeneratedMessageCompanion](
    messageIds: Seq[Int],
    color: String
  ): Future[Try[Response]] = {
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
  ): Future[Try[Response]] = {
    val params =
      colors.map(name => "colors" -> name) ++ doubles.map(v => "doubles" -> v.toString) ++
        floats.map(v => "floats" -> v.toString) ++ longs.map(v => "longs" -> v.toString) ++
        booleans.map(v => "booleans" -> v.toString)
    val uri = uri"$baseUrl/v1/test/array".withParams(params*)
    getMessage(uri)
  }

  def processMessageV5[Response <: GeneratedMessage: GeneratedMessageCompanion](
    intValue: Option[Int]
  ): Future[Try[Response]] = {
    val params = intValue.map(v => "intValue.value" -> v.toString).toMap
    val uri = uri"$baseUrl/v1/test/processMessageV5".withParams(params)
    getMessage(uri)
  }

  def processMessageV6[Response <: GeneratedMessage: GeneratedMessageCompanion](
    messageId: Int,
    userId: String
  ): Future[Try[Response]] = {
    val params = Map("user_id" -> userId)
    val uri = uri"$baseUrl/v1/test/messages/$messageId/map".withParams(params)
    getMessage(uri)
  }

  private def getMessage[Response <: GeneratedMessage: GeneratedMessageCompanion](uri: Uri): Future[Try[Response]] =
    basicRequest.get(uri).response(asString).send(backend).map { response =>
      response.body match {
        case Left(ex)    => throw HttpResponseException(response.code.code, ex)
        case Right(body) => parseBody(body)
      }
    }
}

final case class HttpResponseException(status: Int, message: String) extends RuntimeException(message)
