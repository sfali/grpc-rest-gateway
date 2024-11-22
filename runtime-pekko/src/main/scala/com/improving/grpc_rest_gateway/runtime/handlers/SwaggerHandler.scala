package com.improving
package grpc_rest_gateway
package runtime
package handlers

import runtime.core.*
import org.apache.commons.io.IOUtils
import org.apache.pekko
import pekko.http.scaladsl.model.{ContentType, ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import pekko.http.scaladsl.server.Route
import pekko.http.scaladsl.server.Directives.*

import java.nio.file.{Path, Paths}
import javax.activation.MimetypesFileTypeMap

class SwaggerHandler(handlers: Seq[GrpcGatewayHandler]) {
  import SwaggerHandler.*

  private val mimeTypes = new MimetypesFileTypeMap()
  mimeTypes.addMimeTypes("image/png png PNG")
  mimeTypes.addMimeTypes("text/css css CSS")
  private val indexPage = readSwaggerIndexPage(handlers.map(_.specificationName).distinct.sorted)

  private[runtime] val route: Route =
    pathSingleSlash {
      redirect(DocsLandingPage, StatusCodes.PermanentRedirect)
    } ~ path(DocsPrefix) {
      redirect(DocsLandingPage, StatusCodes.PermanentRedirect)
    } ~ path(DocsPrefix / IndexPage) {
      complete(
        HttpResponse(
          status = StatusCodes.OK,
          entity = HttpEntity(contentType = ContentTypes.`text/html(UTF-8)`, bytes = indexPage.getBytes)
        )
      )
    } ~ path(DocsPrefix / RemainingPath) { rem =>
      val p = Paths.get(s"/$DocsPrefix", rem.toString())
      val resourcePath = SwaggerUiPath.resolve(RootPath.relativize(p).subpath(1, p.getNameCount))
      complete(createResourceResponse(resourcePath))
    } ~ path(SpecsPrefix / RemainingPath) { rem =>
      val resourcePath = RootPath.relativize(Paths.get(s"/$SpecsPrefix", rem.toString()))
      complete(createResourceResponse(resourcePath))
    }

  private def createResourceResponse(path: Path) = {
    val unixPath = separatorsToUnix(path.toString)
    val resource = Option(Thread.currentThread().getContextClassLoader.getResourceAsStream(unixPath))
    resource match {
      case Some(is) =>
        val contentType =
          ContentType.parse(mimeTypes.getContentType(unixPath)) match {
            case Left(value) =>
              println(s"CT: $value")
              ContentTypes.`application/octet-stream`
            case Right(contentType) => contentType
          }
        HttpResponse(
          status = StatusCodes.OK,
          entity = HttpEntity(contentType = contentType, bytes = IOUtils.toByteArray(is))
        )
      case None => HttpResponse(status = StatusCodes.NotFound)
    }
  }

  private def separatorsToUnix(path: String) = path.replace('\\', '/')
}

object SwaggerHandler {
  private val SwaggerUiPath = {
    val swaggerDependency = BuildInfo.allDependencies.filter(_.startsWith("org.webjars:swagger-ui")).head
    val index = swaggerDependency.lastIndexOf(":")
    val version = swaggerDependency.substring(index + 1)
    Paths.get(s"META-INF/resources/webjars/swagger-ui/$version")
  }
  private val SpecsPrefix = "specs"
  private val DocsPrefix = "docs"
  private val IndexPage = "index.html"
  private val DocsLandingPage = s"/$DocsPrefix/$IndexPage"
  private val RootPath = Paths.get("/")

  def apply(handlers: Seq[GrpcGatewayHandler]): SwaggerHandler = new SwaggerHandler(handlers)
}
