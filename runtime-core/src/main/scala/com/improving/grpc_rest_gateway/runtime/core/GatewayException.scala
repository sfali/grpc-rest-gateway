package com.improving
package grpc_rest_gateway
package runtime
package core

import io.grpc.Status.Code

case class GatewayException(statusCode: Int, message: String) extends Exception(message)

object GatewayException {
  def toInvalidArgument(message: String): GatewayException =
    GatewayException(Code.INVALID_ARGUMENT.value(), message)
}
