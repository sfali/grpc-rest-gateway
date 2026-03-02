package com.improving
package grpc_rest_gateway
package runtime
package handlers

import scala.compiletime.asMatchable
import io.grpc.{ManagedChannel, StatusRuntimeException}
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.{FullHttpRequest, HttpResponseStatus}
import scalapb.GeneratedMessage
import scalapb.json4s.JsonFormat

import java.nio.charset.StandardCharsets
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters.*

import runtime.core.GatewayException
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelFutureListener, ChannelInboundHandlerAdapter}
import io.netty.handler.codec.http.*
import org.slf4j.{Logger, LoggerFactory}

@Sharable
abstract class GrpcGatewayHandler(channel: ManagedChannel)(using ec: ExecutionContext)
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

  def shutdown(): Unit = 
    (!channel.isShutdown) match {
      case true => channel.shutdown()
      case false => // Do nothing
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
    *   tuple containing status code and result of the gRPC call
    */
  protected def dispatchCall(method: HttpMethod, uri: String, body: String): Future[(Int, GeneratedMessage)]

  override def channelRead(ctx: ChannelHandlerContext, msg: scala.Any): Unit =
    msg.asMatchable match {
      case req: FullHttpRequest =>
        (supportsCall(req.method(), req.uri())) match {
          case true =>
            val body = req.content().toString(StandardCharsets.UTF_8)

            dispatchCall(req.method(), req.uri(), body)
              .map { case (statusCode, msg) =>
                (statusCode, JsonFormat.toJsonString(msg))
              }
              .map { case (statusCode, json) =>
                val status = HttpResponseStatus.valueOf(statusCode)
                buildFullHttpResponse(
                  requestMsg = req,
                  responseBody = (HttpResponseStatus.NO_CONTENT == status) match {
                    case true => ""
                    case false => json
                  },
                  responseStatus = status,
                  responseContentType = "application/json"
                )
              }
              .recover { case ex: Throwable =>
                logger.error("Error while processing request", ex)
                val (status, responseBody, contentType) = ex match {
                  case gatewayEx: GatewayException =>
                    (GRPC_HTTP_CODE_MAP.getOrElse(gatewayEx.statusCode, HttpResponseStatus.INTERNAL_SERVER_ERROR), 
                     gatewayEx.getMessage, "text/plain")
                  case _ =>
                    (HttpResponseStatus.INTERNAL_SERVER_ERROR, ex.getMessage, "text/plain")
                }
                buildFullHttpResponse(
                  requestMsg = req,
                  responseBody = responseBody,
                  responseStatus = status,
                  responseContentType = contentType
                )
              }
              .foreach { response =>
                ctx.writeAndFlush(response)
              }
          case false =>
            // Pass to next handler in pipeline instead of sending 404
            ctx.fireChannelRead(req)
        }
      case _ => // Do nothing for non-HTTP requests
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

    (configuredPath == path) match {
      case true => flattenQueryParameters
      case false =>
        // "{" has special meaning in regex, replacing "{" and "}" with "#" for now
        val configuredPathElements =
          configuredPath.replaceAll("}", "").replaceAll("\\{", "#").split("/").filterNot(_.isBlank)
        val runtimePathElements = path.split("/").filterNot(_.isBlank)

        // see comments in replacePathParameters
        val configuredToRuntimeDiff = configuredPathElements.diff(runtimePathElements)
        val runtimeToConfiguredDiff = runtimePathElements.diff(configuredPathElements)
        val mismatchPaths = configuredToRuntimeDiff.exists(s => !s.contains("#"))
        
        (!mismatchPaths && configuredToRuntimeDiff.length == runtimeToConfiguredDiff.length) match {
          case true =>
            // now remove "#"
            val pathParameters = configuredToRuntimeDiff.map(_.replaceAll("#", "")).zip(runtimeToConfiguredDiff).toMap.map {
              case (key, value) => key -> Seq(value)
            }
            Map.empty[String, Seq[String]] ++ pathParameters ++ flattenQueryParameters
          case false => flattenQueryParameters
        }
    }
  }

  private def isMatchingPaths(configuredPath: String, runtimePath: String): Boolean =
    (configuredPath == runtimePath) match {
      case true => true
      case false =>
        // "{" has special meaning in regex, replacing "{" and "}" with "#" for now
        val configuredPathElements =
          configuredPath.replaceAll("}", "").replaceAll("\\{", "#").split("/").filterNot(_.isBlank)
        val runtimePathElements = runtimePath.split("/").filterNot(_.isBlank)

        // length of both paths must be equal
        (configuredPathElements.length == runtimePathElements.length) match {
          case true =>
            // scan both arrays other than path variables rest of the path elements must be matching
            val mismatchPath =
              configuredPathElements.zip(runtimePathElements).exists { case (src, target) =>
                !src.startsWith("#") && src != target
              }
            !mismatchPath
          case false => false
        }
    }

}
