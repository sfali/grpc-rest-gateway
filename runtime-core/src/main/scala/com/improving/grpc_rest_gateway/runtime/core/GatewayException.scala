package com.improving
package grpc_rest_gateway
package runtime
package core

case class GatewayException(statusCode: Int, message: String) extends Exception(message)
