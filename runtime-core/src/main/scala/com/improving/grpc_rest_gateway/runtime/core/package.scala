package com.improving
package grpc_rest_gateway
package runtime

import io.grpc.StatusRuntimeException
import scalapb.{GeneratedEnum, GeneratedEnumCompanion, GeneratedMessage, GeneratedMessageCompanion}
import scalapb.json4s.{JsonFormat, JsonFormatException}

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

  def parseBody[M <: GeneratedMessage: GeneratedMessageCompanion](body: String): Try[M] =
    Try(JsonFormat.fromJsonString[M](body)).recoverWith(jsonException2GatewayExceptionPF)

  def parseBodyOptional[M <: GeneratedMessage: GeneratedMessageCompanion](body: String): Try[Option[M]] =
    Try(Option(JsonFormat.fromJsonString[M](body))).recoverWith(jsonException2GatewayExceptionPF)

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

  implicit class ParametersOps(src: Map[String, Seq[String]]) {

    def toIntValue(key: String, defaultValue: String = "0"): Int =
      src.get(key).flatMap(_.headOption).getOrElse(defaultValue).toInt

    def toIntValues(key: String): Seq[Int] = {
      def toValues(values: Seq[String]) = values.map(v => Try(v.toInt).getOrElse(0))
      toValues(src.getOrElse(key, Seq.empty))
    }

    def toLongValue(key: String, defaultValue: String = "0"): Long =
      src.get(key).flatMap(_.headOption).getOrElse(defaultValue).toLong

    def toLongValues(key: String): Seq[Long] = {
      def toValues(values: Seq[String]) = values.map(v => Try(v.toLong).getOrElse(0L))
      toValues(src.getOrElse(key, Seq.empty))
    }

    def toDoubleValue(key: String, defaultValue: String = "0"): Double =
      src.get(key).flatMap(_.headOption).getOrElse(defaultValue).toDouble

    def toDoubleValues(key: String): Seq[Double] = {
      def toValues(values: Seq[String]) = values.map(v => Try(v.toDouble).getOrElse(0.0))
      toValues(src.getOrElse(key, Seq.empty))
    }

    def toFloatValue(key: String, defaultValue: String = "0"): Float =
      src.get(key).flatMap(_.headOption).getOrElse(defaultValue).toFloat

    def toFloatValues(key: String): Seq[Float] = {
      def toValues(values: Seq[String]) = values.map(v => Try(v.toFloat).getOrElse(0.0f))
      toValues(src.getOrElse(key, Seq.empty))
    }

    def toBooleanValue(key: String, defaultValue: String = "false"): Boolean =
      src.get(key).flatMap(_.headOption).getOrElse(defaultValue).toBoolean

    def toBooleanValues(key: String): Seq[Boolean] = {
      def toValues(values: Seq[String]) = values.map(v => Try(v.toBoolean).getOrElse(false))
      toValues(src.getOrElse(key, Seq.empty))
    }

    def toStringValue(key: String): String = src.get(key).flatMap(_.headOption).getOrElse("")

    def toStringValues(key: String): Seq[String] = src.getOrElse(key, Seq.empty)

    def toEnumValue[T <: GeneratedEnum](key: String, companion: GeneratedEnumCompanion[T]): T =
      toEnumValueInternal(toStringValue(key), companion)

    def toEnumValues[T <: GeneratedEnum](key: String, companion: GeneratedEnumCompanion[T]): Seq[T] =
      toStringValues(key).map(v => toEnumValueInternal(v, companion))

    private def toEnumValueInternal[T <: GeneratedEnum](value: String, companion: GeneratedEnumCompanion[T]): T =
      Try(value.toInt).toOption.map(companion.fromValue) match {
        case Some(value) => value
        case None        => companion.fromName(value).getOrElse(companion.fromValue(-1))
      }
  }
}
