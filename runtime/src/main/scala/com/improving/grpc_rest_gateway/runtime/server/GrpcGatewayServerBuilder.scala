package com.improving
package grpc_rest_gateway
package runtime
package server

import runtime.handlers.{GrpcGatewayHandler, MethodNotFoundHandler, SwaggerHandler}
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelInitializer
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.{HttpObjectAggregator, HttpServerCodec}

case class GrpcGatewayServerBuilder(port: Int, services: Seq[GrpcGatewayHandler]) {

  def build(): GrpcGatewayServer = {
    val masterGroup = new NioEventLoopGroup()
    val slaveGroup = new NioEventLoopGroup()
    val bootstrap = new ServerBootstrap()
    bootstrap
      .group(masterGroup, slaveGroup)
      .channel(classOf[NioServerSocketChannel])
      .childHandler(new ChannelInitializer[SocketChannel] {
        override def initChannel(ch: SocketChannel): Unit = {
          ch.pipeline().addLast("codec", new HttpServerCodec())
          ch.pipeline().addLast("aggregator", new HttpObjectAggregator(512 * 1024))
          ch.pipeline().addLast("swagger", new SwaggerHandler(services))
          services.foreach(handler => ch.pipeline().addLast(handler.name, handler))
          ch.pipeline().addLast(new MethodNotFoundHandler())
        }
      })

    new GrpcGatewayServer(port, bootstrap, masterGroup, slaveGroup, services.toList)
  }

}
