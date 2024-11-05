package com.improving
package grpc_rest_gateway
package runtime
package handlers

import java.nio.charset.StandardCharsets
import scalapb.GeneratedMessage
import scalapb.json4s.JsonFormat
import io.grpc.{ManagedChannel, StatusRuntimeException}
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelFutureListener, ChannelHandlerContext, ChannelInboundHandlerAdapter}
import io.netty.handler.codec.http._

import scala.concurrent.{ExecutionContext, Future}

@Sharable
abstract class GrpcGatewayHandler(channel: ManagedChannel)(implicit ec: ExecutionContext)
    extends ChannelInboundHandlerAdapter {

  val name: String
  protected val httpMethodsToUrisMap: Map[String, Seq[String]]

  def shutdown(): Unit = if (!channel.isShutdown) channel.shutdown()

  /** Determine whether current HTTP `method` and `uri` are supported. Any given operation is supported if and only if
    * `google.api.http` option is defined and gRPC function is unary (no streaming, either client or server).
    *
    * @param method
    *   HTTP method
    * @param uri
    *   current URI
    * @return
    *   true if supported, false otherwise
    */
  protected def supportsCall(method: HttpMethod, uri: String): Boolean = {
    val queryString = new QueryStringDecoder(uri)
    val path = queryString.path
    httpMethodsToUrisMap.get(method.name()) match {
      case Some(configuredUris) =>
        val pathsWithParameters = configuredUris.filter(_.contains("}"))
        // case 1: no path element we have an exact match
        configuredUris.contains(path) ||
        // case 2: we have path parameter(s),
        pathsWithParameters.map(cp => replacePathParameters(cp, path)).contains(path)
      case None => false
    }
  }

  /** Makes gRPC call.
    *
    * @param method
    *   HTTP method
    * @param uri
    *   current URI
    * @param body
    *   request body
    * @return
    *   result of the gRPC call
    */
  def unaryCall(method: HttpMethod, uri: String, body: String): Future[GeneratedMessage]

  override def channelRead(ctx: ChannelHandlerContext, msg: scala.Any): Unit =
    msg match {
      case req: FullHttpRequest =>
        if (supportsCall(req.method(), req.uri())) {
          val body = req.content().toString(StandardCharsets.UTF_8)

          unaryCall(req.method(), req.uri(), body)
            .map(JsonFormat.toJsonString)
            .map { json =>
              buildFullHttpResponse(
                requestMsg = req,
                responseBody = json,
                responseStatus = HttpResponseStatus.OK,
                responseContentType = "application/json"
              )
            }
            .recover { case err =>
              val (body, status) = err match {
                case e: GatewayException =>
                  e.details -> GRPC_HTTP_CODE_MAP.getOrElse(e.code, HttpResponseStatus.INTERNAL_SERVER_ERROR)
                case err: StatusRuntimeException =>
                  val grpcStatus = err.getStatus
                  grpcStatus.getDescription -> GRPC_HTTP_CODE_MAP.getOrElse(
                    grpcStatus.getCode.value(),
                    HttpResponseStatus.INTERNAL_SERVER_ERROR
                  )
                case _ => "Internal error" -> HttpResponseStatus.INTERNAL_SERVER_ERROR
              }

              buildFullHttpResponse(
                requestMsg = req,
                responseBody = body,
                responseStatus = status,
                responseContentType = "application/text"
              )
            }
            .foreach(resp => ctx.writeAndFlush(resp).addListener(ChannelFutureListener.CLOSE))

        } else super.channelRead(ctx, msg)
      case _ => super.channelRead(ctx, msg)
    }

  private def replacePathParameters(configuredPath: String, runtimePath: String): String = {
    val configuredPathElements = configuredPath.replaceAll("}", "").replaceAll("\\{", "").split("/")
    val runtimePathElements = runtimePath.split("/")
    if (configuredPathElements.length == runtimePathElements.length) {
      val diff1 = configuredPathElements.diff(runtimePathElements)
      val diff2 = runtimePathElements.diff(configuredPathElements)
      val pathParameters = diff1.zip(diff2).toMap
      pathParameters.foldLeft(runtimePath) { case (result, (key, value)) =>
        result.replaceAll(key, value)
      }
    } else configuredPath
  }
}
