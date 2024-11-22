package com.improving
package grpc_rest_gateway
package runtime

import io.grpc.Status.Code
import io.grpc.StatusRuntimeException
import scalapb.GeneratedMessage
import scalapb.json4s.JsonFormatException

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Try}

package object core {

  implicit class StatusRuntimeExceptionOps(src: StatusRuntimeException) {

    def toGatewayException: GatewayException =
      GatewayException(src.getStatus.getCode.value(), src.getStatus.getDescription)
  }

  def jsonException2GatewayExceptionPF[U]: PartialFunction[Throwable, Try[U]] = {
    case _: NoSuchElementException =>
      Failure(GatewayException(Code.INVALID_ARGUMENT.value(), "Wrong json input. Check proto file"))
    case err: JsonFormatException =>
      Failure(GatewayException(Code.INVALID_ARGUMENT.value(), "Wrong json syntax: " + err.msg))
    case err =>
      Failure(
        GatewayException(Code.INVALID_ARGUMENT.value(), "Wrong json input. Check proto file. Details: " + err.getMessage)
      )
  }

  def toResponse[IN <: GeneratedMessage, OUT <: GeneratedMessage](
    in: Try[IN],
    dispatchCall: IN => Future[OUT]
  )(implicit
    ec: ExecutionContext
  ): Future[OUT] =
    Future
      .fromTry(in)
      .flatMap(dispatchCall)
      .recoverWith { case ex: StatusRuntimeException =>
        Future.failed(ex.toGatewayException)
      }

}
