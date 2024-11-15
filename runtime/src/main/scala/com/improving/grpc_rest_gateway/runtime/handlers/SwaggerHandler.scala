package com.improving
package grpc_rest_gateway
package runtime
package handlers

import java.nio.file.{Path, Paths}
import javax.activation.MimetypesFileTypeMap
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelFutureListener, ChannelHandlerContext, ChannelInboundHandlerAdapter}
import io.netty.handler.codec.http.*
import io.netty.util.CharsetUtil
import org.apache.commons.io.IOUtils

import scala.io.Source
import scala.util.{Failure, Success, Using}

object SwaggerHandler {
  private val SwaggerUiPath = {
    val swaggerDependency = BuildInfo.allDependencies.filter(_.startsWith("org.webjars:swagger-ui")).head
    val index = swaggerDependency.lastIndexOf(":")
    val version = swaggerDependency.substring(index + 1)
    Paths.get(s"META-INF/resources/webjars/swagger-ui/$version")
  }
  private val SpecsPrefix = Paths.get("/specs/")
  private val DocsPrefix = Paths.get("/docs/")
  private val DocsLandingPage = Paths.get("/docs/index.html")
  private val RootPath = Paths.get("/")
}

@Sharable
class SwaggerHandler(services: Seq[GrpcGatewayHandler]) extends ChannelInboundHandlerAdapter {
  import SwaggerHandler.*

  // TODO: figure out how to cross compile and pattern match
  override def channelRead(ctx: ChannelHandlerContext, msg: scala.Any): Unit =
    if (msg.isInstanceOf[FullHttpRequest]) {
      val req = msg.asInstanceOf[FullHttpRequest]
      val queryString = new QueryStringDecoder(req.uri())
      val path = Paths.get(queryString.path())
      val res = path match {
        case RootPath                      => Some(createRedirectResponse(req, DocsLandingPage))
        case DocsPrefix                    => Some(createRedirectResponse(req, DocsLandingPage))
        case DocsLandingPage               => Some(createStringResponse(req, indexPage))
        case p if p.startsWith(DocsPrefix) =>
          // swagger UI loading its own resources
          val resourcePath = SwaggerUiPath.resolve(RootPath.relativize(path).subpath(1, path.getNameCount))
          Some(createResourceResponse(req, resourcePath))
        case p if p.startsWith(SpecsPrefix) =>
          // swagger UI loading up spec file
          Some(createResourceResponse(req, RootPath.relativize(path)))
        case _ => None
      }
      res match {
        case Some(response) => ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE)
        case None           => super.channelRead(ctx, msg)
      }
    } else super.channelRead(ctx, msg)

  private def createRedirectResponse(req: FullHttpRequest, path: Path) = {
    val res = new DefaultFullHttpResponse(req.protocolVersion(), HttpResponseStatus.TEMPORARY_REDIRECT)
    res.headers().add(HttpHeaderNames.LOCATION, path.toString)
    res
  }

  private def createStringResponse(req: FullHttpRequest, value: String) = {
    val res = new DefaultFullHttpResponse(
      req.protocolVersion(),
      HttpResponseStatus.OK,
      Unpooled.copiedBuffer(value, CharsetUtil.UTF_8)
    )
    res.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html")
    setCommonHeaders(req, res)
    res
  }

  private def createResourceResponse(req: FullHttpRequest, path: Path) = {
    val resource = Thread.currentThread().getContextClassLoader.getResourceAsStream(separatorsToUnix(path.toString))
    val res = resource match {
      case null => new DefaultFullHttpResponse(req.protocolVersion(), HttpResponseStatus.NOT_FOUND)
      case some =>
        val bytes = IOUtils.toByteArray(some)
        val res = new DefaultFullHttpResponse(
          req.protocolVersion(),
          HttpResponseStatus.OK,
          Unpooled.copiedBuffer(bytes)
        )
        res.headers().set(HttpHeaderNames.CONTENT_TYPE, mimeTypes.getContentType(separatorsToUnix(path.toString)))
        setCommonHeaders(req, res)
        res
    }
    res
  }

  private def setCommonHeaders(req: FullHttpRequest, res: FullHttpResponse): Unit = {
    HttpUtil.setContentLength(res, res.content().readableBytes())
    HttpUtil.setKeepAlive(res, HttpUtil.isKeepAlive(req))
  }

  private def separatorsToUnix(path: String) = path.replace('\\', '/')

  private val mimeTypes = new MimetypesFileTypeMap()
  mimeTypes.addMimeTypes("image/png png PNG")
  mimeTypes.addMimeTypes("text/css css CSS")
  private val specificationNames = services.map(_.specificationName).distinct.sorted
  private val serviceUrls = specificationNames.map(s => s"{url: '/specs/$s.yml', name: '$s'}").mkString(", ")
  private val serviceNames = specificationNames.mkString(", ")
  private val indexPage =
    Using(
      Source
        .fromInputStream(Thread.currentThread().getContextClassLoader.getResourceAsStream("index.html"))
    ) { source =>
      source
        .getLines()
        .mkString(System.lineSeparator())
        .replaceAll("\\{serviceUrls}", serviceUrls)
        .replaceAll("\\{serviceNames}", serviceNames)
    } match {
      case Failure(ex)   => throw ex
      case Success(html) => html
    }

}
