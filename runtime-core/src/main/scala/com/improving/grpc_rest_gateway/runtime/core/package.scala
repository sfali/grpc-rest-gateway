package com.improving
package grpc_rest_gateway
package runtime

import io.grpc.StatusRuntimeException
import scalapb.GeneratedMessage
import scalapb.json4s.JsonFormatException

import java.nio.file.{Path, Paths}
import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source
import scala.util.{Failure, Success, Try, Using}

package object core {

  lazy val SwaggerUiPath: Path = {
    val swaggerDependency = BuildInfo.allDependencies.filter(_.startsWith("org.webjars:swagger-ui")).head
    val index = swaggerDependency.lastIndexOf(":")
    val version = swaggerDependency.substring(index + 1)
    Paths.get(s"META-INF/resources/webjars/swagger-ui/$version")
  }

  implicit class StatusRuntimeExceptionOps(src: StatusRuntimeException) {

    def toGatewayException: GatewayException =
      GatewayException(src.getStatus.getCode.value(), src.getStatus.getDescription)
  }

  def jsonException2GatewayExceptionPF[U]: PartialFunction[Throwable, Try[U]] = {
    case _: NoSuchElementException =>
      Failure(GatewayException.toInvalidArgument("Wrong json input. Check proto file"))
    case err: JsonFormatException =>
      Failure(GatewayException.toInvalidArgument("Wrong json syntax: " + err.msg))
    case err =>
      Failure(
        GatewayException.toInvalidArgument("Wrong json input. Check proto file. Details: " + err.getMessage)
      )
  }

  def toResponse[IN <: GeneratedMessage, OUT <: GeneratedMessage](
    in: Try[IN],
    dispatchCall: IN => Future[OUT]
  )(implicit
    ec: ExecutionContext
  ): Future[OUT] =
    Future
      .fromTry(in)
      .flatMap(dispatchCall)
      .recoverWith { case ex: StatusRuntimeException =>
        Future.failed(ex.toGatewayException)
      }

  def readSwaggerIndexPage(specificationNames: Seq[String]): String = {
    val serviceUrls = specificationNames.map(s => s"{url: '/specs/$s.yml', name: '$s'}").mkString(", ")
    val serviceNames = specificationNames.mkString(", ")
    Using(
      Source
        .fromInputStream(Thread.currentThread().getContextClassLoader.getResourceAsStream("swagger/index.html"))
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
}
