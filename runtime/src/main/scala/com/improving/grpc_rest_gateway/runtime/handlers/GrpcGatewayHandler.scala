package com.improving
package grpc_rest_gateway
package runtime
package handlers

import io.grpc.{ManagedChannel, StatusRuntimeException}
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelFutureListener, ChannelHandlerContext, ChannelInboundHandlerAdapter}
import io.netty.handler.codec.http._
import org.slf4j.{Logger, LoggerFactory}
import scalapb.GeneratedMessage
import scalapb.json4s.JsonFormat

import java.nio.charset.StandardCharsets
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

@Sharable
abstract class GrpcGatewayHandler(channel: ManagedChannel)(implicit ec: ExecutionContext)
    extends ChannelInboundHandlerAdapter
    with PathMatchingSupport {

  protected val logger: Logger = LoggerFactory.getLogger(getClass)

  val name: String

  def shutdown(): Unit = if (!channel.isShutdown) channel.shutdown()

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
  protected def dispatchCall(method: HttpMethod, uri: String, body: String): Future[GeneratedMessage]

  override def channelRead(ctx: ChannelHandlerContext, msg: scala.Any): Unit =
    msg match {
      case req: FullHttpRequest =>
        if (supportsCall(req.method(), req.uri())) {
          val body = req.content().toString(StandardCharsets.UTF_8)

          dispatchCall(req.method(), req.uri(), body)
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
                case ex =>
                  logger.warn("unable to generate json response", ex)
                  "Internal error" -> HttpResponseStatus.INTERNAL_SERVER_ERROR
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
}

/** Helper trait to make URI path matching. This is done to make testing easier.
  *
  * Our aim is to match given incoming URL and match it to configured path in the protobuf.
  *
  * There are two cases:
  *
  *   1. When there is no path element, this is easy case we can have exact match, for example, `/v1/messages`.
  *   1. When there is path element, we can not have exact match just by comparing two strings, for example, if
  *      configured value is `/v1/messages/{message_id}/users/{user_id}` and runtime value is `/v1/messages/1/users/1`.
  */
trait PathMatchingSupport {
  protected val httpMethodsToUrisMap: Map[String, Seq[String]]

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
    httpMethodsToUrisMap.get(method.name) match {
      case Some(configuredUris) =>
        val pathsWithParameters = configuredUris.filter(_.contains("}"))
        // case 1: no path element we have an exact match
        configuredUris.contains(path) ||
        // case 2: we have path parameter(s)
        pathsWithParameters.map(cp => replacePathParameters(cp, path)).contains(path)
      case None => false
    }
  }

  protected def isSupportedCall(
    configuredMethodName: String,
    configuredPath: String,
    runtimeMethodName: String,
    runtimePath: String
  ): Boolean =
    configuredMethodName == runtimeMethodName && replacePathParameters(configuredPath, runtimePath) == runtimePath

  protected def mergeParameters(configuredPath: String, queryString: QueryStringDecoder): Map[String, String] = {
    val path = queryString.path()
    val flattenQueryParameters =
      queryString
        .parameters()
        .asScala
        .map { case (key, values) =>
          key -> values.asScala.head
        }
        .toMap

    if (configuredPath == path) flattenQueryParameters
    else {
      // "{" has special meaning in regex, replacing "{" and "}" with "#" for now
      val configuredPathElements =
        configuredPath.replaceAll("}", "#").replaceAll("\\{", "#").split("/").filterNot(_.isBlank)
      val runtimePathElements = path.split("/").filterNot(_.isBlank)

      // see comments in replacePathParameters
      val configuredToRuntimeDiff = configuredPathElements.diff(runtimePathElements)
      val runtimeToConfiguredDiff = runtimePathElements.diff(configuredPathElements)
      val mismatchPaths = configuredToRuntimeDiff.exists(s => !s.contains("#"))
      if (!mismatchPaths && configuredToRuntimeDiff.length == runtimeToConfiguredDiff.length) {
        // now remove "#"
        val pathParameters = configuredToRuntimeDiff.map(_.replaceAll("#", "")).zip(runtimeToConfiguredDiff).toMap
        Map.empty[String, String] ++ pathParameters ++ flattenQueryParameters
      } else flattenQueryParameters
    }
  }

  // TODO: is there any efficient way to do this?
  private[handlers] def replacePathParameters(configuredPath: String, runtimePath: String): String =
    if (configuredPath == runtimePath) configuredPath
    else {
      // "{" has special meaning in regex, replacing "{" and "}" with "#" for now
      val configuredPathElements =
        configuredPath.replaceAll("}", "#").replaceAll("\\{", "#").split("/").filterNot(_.isBlank)
      val runtimePathElements = runtimePath.split("/").filterNot(_.isBlank)
      val configuredToRuntimeDiff = configuredPathElements.diff(runtimePathElements)
      val runtimeToConfiguredDiff = runtimePathElements.diff(configuredPathElements)

      // Difference in two URIs should only be with path parameters (which are enclosed in "#"), if there is an
      // element which doesn't have "#" then we have a mismatch
      // For example, /v1/messages/{message_id}/sub/{sub.subfield} and runtime path is /v1/messages/1/users/1
      // Then `configuredToRuntimeDiff` will be [#message_id#, sub, #sub.subfield#] and runtimeToConfiguredDiff
      // will be [1, users, 1], we have a mismatch
      val mismatchPaths = configuredToRuntimeDiff.exists(s => !s.contains("#"))
      if (!mismatchPaths && configuredToRuntimeDiff.length == runtimeToConfiguredDiff.length) {
        val pathParameters = configuredToRuntimeDiff.zip(runtimeToConfiguredDiff).toMap
        pathParameters.foldLeft(runtimePath) { case (result, (key, value)) =>
          result.replaceAll(key, value)
        }
      } else configuredPath
    }
}
