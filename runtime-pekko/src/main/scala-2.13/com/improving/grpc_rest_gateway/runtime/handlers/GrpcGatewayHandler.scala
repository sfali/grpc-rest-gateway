package com.improving
package grpc_rest_gateway
package runtime
package handlers

import runtime.core.internal.*
import org.apache.pekko
import pekko.http.scaladsl.server.Directives.*
import pekko.http.scaladsl.server.Route
import scalapb.GeneratedMessage

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

trait GrpcGatewayHandler extends GrpcGatewayHandlerExt {

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
}
