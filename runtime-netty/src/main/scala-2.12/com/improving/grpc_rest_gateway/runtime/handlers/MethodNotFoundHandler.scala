package com.improving
package grpc_rest_gateway
package runtime
package handlers

import io.netty.channel.{ChannelFutureListener, ChannelHandlerContext, ChannelInboundHandlerAdapter}
import io.netty.handler.codec.http.{FullHttpRequest, HttpResponseStatus}

class MethodNotFoundHandler extends ChannelInboundHandlerAdapter {

  // TODO: figure out how to cross compile and pattern match
  override def channelRead(ctx: ChannelHandlerContext, msg: scala.Any): Unit =
    if (msg.isInstanceOf[FullHttpRequest]) {
      val req = msg.asInstanceOf[FullHttpRequest]
      ctx
        .writeAndFlush {
          buildFullHttpResponse(
            requestMsg = req,
            responseBody = "Method isn't supported",
            responseStatus = HttpResponseStatus.BAD_REQUEST,
            responseContentType = "application/text"
          )
        }
        .addListener(ChannelFutureListener.CLOSE)
    } else super.channelRead(ctx, msg)
}
