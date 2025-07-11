package com.improving
package grpc_rest_gateway
package runtime
package handlers

import runtime.core.*
import io.grpc.Status.Code
import org.apache.pekko
import pekko.http.scaladsl.server.Directives.*
import pekko.http.scaladsl.server.{ExceptionHandler, Route}
import pekko.http.scaladsl.model.*
import scalapb.GeneratedMessage
import scalapb.json4s.JsonFormat

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

trait GrpcGatewayHandler {

  private val GrpcToStatusCodes: Map[Int, StatusCode] = Map(
    Code.OK.value() -> StatusCodes.OK,
    Code.CANCELLED.value() -> StatusCodes.Gone,
    Code.UNKNOWN.value() -> StatusCodes.NotFound,
    Code.INVALID_ARGUMENT.value() -> StatusCodes.BadRequest,
    Code.DEADLINE_EXCEEDED.value() -> StatusCodes.GatewayTimeout,
    Code.NOT_FOUND.value() -> StatusCodes.NotFound,
    Code.ALREADY_EXISTS.value() -> StatusCodes.Conflict,
    Code.PERMISSION_DENIED.value() -> StatusCodes.Forbidden,
    Code.RESOURCE_EXHAUSTED.value() -> StatusCodes.InsufficientStorage,
    Code.FAILED_PRECONDITION.value() -> StatusCodes.PreconditionFailed,
    Code.ABORTED.value() -> StatusCodes.Gone,
    Code.OUT_OF_RANGE.value() -> StatusCodes.BadRequest,
    Code.UNIMPLEMENTED.value() -> StatusCodes.NotImplemented,
    Code.INTERNAL.value() -> StatusCodes.InternalServerError,
    Code.UNAVAILABLE.value() -> StatusCodes.NotAcceptable,
    Code.DATA_LOSS.value() -> StatusCodes.PartialContent,
    Code.UNAUTHENTICATED.value() -> StatusCodes.Unauthorized
  ).withDefaultValue(StatusCodes.InternalServerError)

  protected def exceptionHandler: ExceptionHandler = ExceptionHandler { case ex: GatewayException =>
    complete(
      HttpResponse(
        status = GrpcToStatusCodes(ex.statusCode.intValue()),
        entity = HttpEntity(contentType = ContentTypes.`text/plain(UTF-8)`, bytes = ex.message.getBytes)
      )
    )
  }

  private def getStatusCode(value: Int): StatusCode = StatusCodes.getForKey(value).getOrElse(StatusCodes.OK)

  protected def completeResponse[IN <: GeneratedMessage, OUT <: GeneratedMessage](
    in: Try[IN],
    dispatchCall: IN => Future[OUT],
    statusCodeValue: Int
  )(implicit
    ec: ExecutionContext
  ): Route = {
    val statusCode = getStatusCode(statusCodeValue)
    val eventualResponse =
      toResponse(in, dispatchCall, statusCodeValue)
        .map(_._2)
        .map(toHttpResponse(statusCode))
    onComplete(eventualResponse) {
      case Failure(ex)       => complete(ex)
      case Success(response) => complete(response)
    }
  }

  private def toHttpResponse[M <: GeneratedMessage](statusCode: StatusCode)(msg: M): HttpResponse =
    statusCode match {
      case StatusCodes.NoContent =>
        HttpResponse(
          status = statusCode,
          entity = HttpEntity(contentType = ContentTypes.`application/json`, bytes = Array.empty[Byte])
        )
      case _ =>
        HttpResponse(
          status = statusCode,
          entity =
            HttpEntity(contentType = ContentTypes.`application/json`, bytes = JsonFormat.toJsonString(msg).getBytes)
        )
    }

  val specificationName: String
  val route: Route
}
