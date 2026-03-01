package com.improving
package grpc_rest_gateway
package runtime
package handlers

import scala.compiletime.asMatchable
import io.netty.channel.{ChannelFutureListener, ChannelHandlerContext, ChannelInboundHandlerAdapter}
import io.netty.handler.codec.http.{FullHttpRequest, HttpResponseStatus}

class MethodNotFoundHandler extends ChannelInboundHandlerAdapter {

  override def channelRead(ctx: ChannelHandlerContext, msg: scala.Any): Unit =
    msg.asMatchable match {
      case req: FullHttpRequest =>
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
      case _ => super.channelRead(ctx, msg)
    }
}
