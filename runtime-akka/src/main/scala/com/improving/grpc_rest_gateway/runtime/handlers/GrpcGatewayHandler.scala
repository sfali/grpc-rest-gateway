package com.improving
package grpc_rest_gateway
package runtime
package handlers

import runtime.core.*
import io.grpc.Status.Code
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.{ExceptionHandler, Route}
import akka.http.scaladsl.model.*
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

  protected def completeResponse[IN <: GeneratedMessage, OUT <: GeneratedMessage](
    in: Try[IN],
    dispatchCall: IN => Future[OUT],
    statusCode: StatusCode = StatusCodes.OK
  )(implicit
    ec: ExecutionContext
  ): Route = {
    val eventualResponse =
      toResponse(in, dispatchCall)
        .map(toHttpResponse(statusCode))
    onComplete(eventualResponse) {
      case Failure(ex)       => complete(ex)
      case Success(response) => complete(response)
    }
  }

  private def toHttpResponse[M <: GeneratedMessage](statusCode: StatusCode)(msg: M): HttpResponse =
    HttpResponse(
      status = statusCode,
      entity = HttpEntity(contentType = ContentTypes.`application/json`, bytes = JsonFormat.toJsonString(msg).getBytes)
    )

  val specificationName: String
  val route: Route
}
