package com.improving
package grpc_rest_gateway
package runtime
package handlers

import runtime.core.*
import org.apache.commons.io.IOUtils
import org.apache.pekko
import pekko.http.scaladsl.model.*
import pekko.http.scaladsl.server.Directives.*
import pekko.http.scaladsl.server.Route

import java.nio.file.{Path, Paths}
import javax.activation.MimetypesFileTypeMap

/** Swagger handler for serving OpenAPI documentation and Swagger UI.
  *
  * @param specsPrefix
  *   The root path where specification files are served from, e.g. "specs" (without leading slash)
  * @param specificationNames
  *   The sequence of specification names to include in the Swagger UI
  */
class SwaggerHandler(specsPrefix: String, specificationNames: Seq[String]) {
  import SwaggerHandler.*

  private val mimeTypes = new MimetypesFileTypeMap()
  mimeTypes.addMimeTypes("image/png png PNG")
  mimeTypes.addMimeTypes("text/css css CSS")
  private val indexPage = readSwaggerIndexPage(specsPrefix, specificationNames.distinct.sorted)

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
    } ~ path(specsPrefix / RemainingPath) { rem =>
      val resourcePath = RootPath.relativize(Paths.get(s"/$specsPrefix", rem.toString()))
      complete(createResourceResponse(resourcePath))
    } ~ path(RemainingPath) { rem =>
      if (rem.toString.endsWith(".yml") || rem.toString.endsWith(".yaml")) {
        val resourcePath = RootPath.relativize(Paths.get(s"/$specsPrefix", rem.toString()))
        complete(createResourceResponse(resourcePath))
      } else {
        reject() // allow concat(GatewayServer) to try gRPC routes; do not 404 all non-Swagger paths
      }
    }

  private def createResourceResponse(path: Path) = {
    val unixPath = separatorsToUnix(path.toString)
    val resource = Option(Thread.currentThread().getContextClassLoader.getResourceAsStream(unixPath))
    resource match {
      case Some(is) =>
        val contentType =
          ContentType.parse(mimeTypes.getContentType(unixPath)) match {
            case Left(_)            => ContentTypes.`application/octet-stream`
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
  private val DocsPrefix = "docs"
  private val IndexPage = "index.html"
  private val DocsLandingPage = s"/$DocsPrefix/$IndexPage"
  private val RootPath = Paths.get("/")

  def apply(specsPrefix: String, specificationNames: Seq[String]): SwaggerHandler =
    new SwaggerHandler(specsPrefix, specificationNames)
}
