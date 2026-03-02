package com.improving
package grpc_rest_gateway
package runtime
package core

import io.grpc.StatusRuntimeException
import scalapb.GeneratedMessage

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

package object internal {

  def toResponse[IN <: GeneratedMessage, OUT <: GeneratedMessage](
      in: Try[IN],
      dispatchCall: IN => Future[OUT],
      statusCode: Int
      )(using
       ec: ExecutionContext
      ): Future[(Int, OUT)] = {
    in match {
      case Success(parsedIn) =>
        val resultFuture = dispatchCall(parsedIn)
        resultFuture.map(response => (statusCode, response))
          .recoverWith { case ex: StatusRuntimeException =>
            Future.failed(toGatewayException(ex))
          }
      case Failure(ex) =>
        Future.failed(ex)
    }
  }
}
