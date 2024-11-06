package com.improving
package grpc_rest_gateway
package runtime
package server

import runtime.handlers.GrpcGatewayHandler
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.{ChannelFuture, EventLoopGroup}

class GrpcGatewayServer private[server] (
  port: Int,
  bootstrap: ServerBootstrap,
  masterGroup: EventLoopGroup,
  slaveGroup: EventLoopGroup,
  services: List[GrpcGatewayHandler]) {
  private var channel: ChannelFuture = _

  def start(): Unit = channel = bootstrap.bind(port).sync()

  def shutdown(): Unit = {
    slaveGroup.shutdownGracefully()
    masterGroup.shutdownGracefully()
    services.foreach(_.shutdown())
    channel.channel().closeFuture().sync()
  }
}
