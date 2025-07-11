package com.improving
package grpc_rest_gateway
package runtime
package handlers

import runtime.core.GatewayException
import io.grpc.ManagedChannel
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelFutureListener, ChannelHandlerContext, ChannelInboundHandlerAdapter}
import io.netty.handler.codec.http.*
import org.slf4j.{Logger, LoggerFactory}
import scalapb.GeneratedMessage
import scalapb.json4s.JsonFormat

import java.nio.charset.StandardCharsets
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters.*

@Sharable
abstract class GrpcGatewayHandler(channel: ManagedChannel)(implicit ec: ExecutionContext)
    extends ChannelInboundHandlerAdapter
    with PathMatchingSupport {

  protected val logger: Logger = LoggerFactory.getLogger(getClass)

  /** Name of the service
    */
  val serviceName: String

  /** Name of OpenAPI yaml file (without extension) which contains OpenAPI specification for this service. This would be
    * the name of the proto file.
    */
  val specificationName: String

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
    *   tuple containing status code and result of the gRPC call
    */
  protected def dispatchCall(method: HttpMethod, uri: String, body: String): Future[(Int, GeneratedMessage)]

  // TODO: figure out how to cross compile and pattern match
  override def channelRead(ctx: ChannelHandlerContext, msg: scala.Any): Unit =
    if (msg.isInstanceOf[FullHttpRequest]) {
      val req = msg.asInstanceOf[FullHttpRequest]
      if (supportsCall(req.method(), req.uri())) {
        val body = req.content().toString(StandardCharsets.UTF_8)

        dispatchCall(req.method(), req.uri(), body)
          .map { case (statusCode, msg) =>
            (statusCode, JsonFormat.toJsonString(msg))
          }
          .map { case (statusCode, json) =>
            val status = HttpResponseStatus.valueOf(statusCode)
            buildFullHttpResponse(
              requestMsg = req,
              responseBody = if (HttpResponseStatus.NO_CONTENT == status) "" else json,
              responseStatus = status,
              responseContentType = "application/json"
            )
          }
          .recover { case err =>
            val (body, status) = err match {
              case e: GatewayException =>
                e.message -> GRPC_HTTP_CODE_MAP.getOrElse(e.statusCode, HttpResponseStatus.INTERNAL_SERVER_ERROR)
              case ex =>
                logger.warn("unable to generate json response", ex)
                s"Internal error: ${ex.getMessage}" -> HttpResponseStatus.INTERNAL_SERVER_ERROR
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
    } else super.channelRead(ctx, msg)
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
      case Some(configuredUris) => configuredUris.exists(configuredUri => isMatchingPaths(configuredUri, path))
      case None                 => false
    }
  }

  protected def isSupportedCall(
    configuredMethodName: String,
    configuredPath: String,
    runtimeMethodName: String,
    runtimePath: String
  ): Boolean = configuredMethodName == runtimeMethodName && isMatchingPaths(configuredPath, runtimePath)

  protected def mergeParameters(configuredPath: String, queryString: QueryStringDecoder): Map[String, Seq[String]] = {
    val path = queryString.path()
    val flattenQueryParameters =
      queryString
        .parameters()
        .asScala
        .map { case (key, values) =>
          key -> values.asScala.toSeq
        }
        .toMap

    if (configuredPath == path) flattenQueryParameters
    else {
      // "{" has special meaning in regex, replacing "{" and "}" with "#" for now
      val configuredPathElements =
        configuredPath.replaceAll("}", "").replaceAll("\\{", "#").split("/").filterNot(_.isBlank)
      val runtimePathElements = path.split("/").filterNot(_.isBlank)

      // see comments in replacePathParameters
      val configuredToRuntimeDiff = configuredPathElements.diff(runtimePathElements)
      val runtimeToConfiguredDiff = runtimePathElements.diff(configuredPathElements)
      val mismatchPaths = configuredToRuntimeDiff.exists(s => !s.contains("#"))
      if (!mismatchPaths && configuredToRuntimeDiff.length == runtimeToConfiguredDiff.length) {
        // now remove "#"
        val pathParameters = configuredToRuntimeDiff.map(_.replaceAll("#", "")).zip(runtimeToConfiguredDiff).toMap.map {
          case (key, value) => key -> Seq(value)
        }
        Map.empty[String, Seq[String]] ++ pathParameters ++ flattenQueryParameters
      } else flattenQueryParameters
    }
  }

  private def isMatchingPaths(configuredPath: String, runtimePath: String): Boolean =
    if (configuredPath == runtimePath) true
    else {
      // "{" has special meaning in regex, replacing "{" and "}" with "#" for now
      val configuredPathElements =
        configuredPath.replaceAll("}", "").replaceAll("\\{", "#").split("/").filterNot(_.isBlank)
      val runtimePathElements = runtimePath.split("/").filterNot(_.isBlank)

      // length of both paths must be equal
      if (configuredPathElements.length == runtimePathElements.length) {
        // scan both arrays other than path variables rest of the path elements must be matching
        val mismatchPath =
          configuredPathElements.zip(runtimePathElements).exists { case (src, target) =>
            !src.startsWith("#") && src != target
          }
        !mismatchPath
      } else false
    }
}
