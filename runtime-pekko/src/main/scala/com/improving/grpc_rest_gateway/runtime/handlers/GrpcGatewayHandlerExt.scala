package com.improving
package grpc_rest_gateway
package runtime
package handlers

import runtime.core.GatewayException
import io.grpc.Status.Code
import org.apache.pekko
import pekko.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCode, StatusCodes}
import pekko.http.scaladsl.server.Directives.complete
import pekko.http.scaladsl.server.{ExceptionHandler, Route}
import scalapb.GeneratedMessage
import scalapb.json4s.JsonFormat

trait GrpcGatewayHandlerExt {

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

  protected def getStatusCode(value: Int): StatusCode = StatusCodes.getForKey(value).getOrElse(StatusCodes.OK)

  protected def toHttpResponse[M <: GeneratedMessage](statusCode: StatusCode)(msg: M): HttpResponse =
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
