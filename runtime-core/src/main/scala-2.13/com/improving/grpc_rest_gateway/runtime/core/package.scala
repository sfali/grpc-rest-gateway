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
    val version = BuildInfo.swaggerUiVersion
    Paths.get(s"META-INF/resources/webjars/swagger-ui/$version")
  }

  object StatusRuntimeExceptionOps {
    def toGatewayException(src: StatusRuntimeException): GatewayException =
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

  // Scala 2.12/2.13 version using implicit parameter
  def toResponse[IN <: GeneratedMessage, OUT <: GeneratedMessage](
    in: Try[IN],
    dispatchCall: IN => Future[OUT],
    statusCode: Int
  )(implicit ec: ExecutionContext): Future[(Int, OUT)] = {
    in match {
      case Success(parsedIn) =>
        val resultFuture = dispatchCall(parsedIn)
        resultFuture.map(response => (statusCode, response))(ec)
          .recoverWith { case ex: StatusRuntimeException =>
            Future.failed(StatusRuntimeExceptionOps.toGatewayException(ex))
          }(ec)
      case Failure(ex) =>
        Future.failed(ex)
    }
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
        .replace("{serviceNames}", serviceNames)
        .replace("{serviceUrls}", serviceUrls)
    }.get
  }

  object ParametersOps {
    def toIntValue(src: Map[String, Seq[String]], key: String, defaultValue: String = "0"): Int =
      src.get(key).flatMap(_.headOption).getOrElse(defaultValue).toInt

    def toIntValues(src: Map[String, Seq[String]], key: String): Seq[Int] = {
      def toValues(values: Seq[String]) = values.map(v => Try(v.toInt).getOrElse(0))
      toValues(src.getOrElse(key, Seq.empty))
    }

    def toLongValue(src: Map[String, Seq[String]], key: String, defaultValue: String = "0"): Long =
      src.get(key).flatMap(_.headOption).getOrElse(defaultValue).toLong

    def toLongValues(src: Map[String, Seq[String]], key: String): Seq[Long] = {
      def toValues(values: Seq[String]) = values.map(v => Try(v.toLong).getOrElse(0L))
      toValues(src.getOrElse(key, Seq.empty))
    }

    def toDoubleValue(src: Map[String, Seq[String]], key: String, defaultValue: String = "0"): Double =
      src.get(key).flatMap(_.headOption).getOrElse(defaultValue).toDouble

    def toDoubleValues(src: Map[String, Seq[String]], key: String): Seq[Double] = {
      def toValues(values: Seq[String]) = values.map(v => Try(v.toDouble).getOrElse(0.0))
      toValues(src.getOrElse(key, Seq.empty))
    }

    def toFloatValue(src: Map[String, Seq[String]], key: String, defaultValue: String = "0"): Float =
      src.get(key).flatMap(_.headOption).getOrElse(defaultValue).toFloat

    def toFloatValues(src: Map[String, Seq[String]], key: String): Seq[Float] = {
      def toValues(values: Seq[String]) = values.map(v => Try(v.toFloat).getOrElse(0.0f))
      toValues(src.getOrElse(key, Seq.empty))
    }

    def toBooleanValue(src: Map[String, Seq[String]], key: String, defaultValue: String = "false"): Boolean =
      src.get(key).flatMap(_.headOption).getOrElse(defaultValue).toBoolean

    def toBooleanValues(src: Map[String, Seq[String]], key: String): Seq[Boolean] = {
      def toValues(values: Seq[String]) = values.map(v => Try(v.toBoolean).getOrElse(false))
      toValues(src.getOrElse(key, Seq.empty))
    }

    def toStringValue(src: Map[String, Seq[String]], key: String): String = src.get(key).flatMap(_.headOption).getOrElse("")

    def toStringValues(src: Map[String, Seq[String]], key: String): Seq[String] = src.getOrElse(key, Seq.empty)

    def toEnumValue[T <: GeneratedEnum](src: Map[String, Seq[String]], key: String, companion: GeneratedEnumCompanion[T]): T =
      toEnumValueInternal(toStringValue(src, key), companion)

    def toEnumValues[T <: GeneratedEnum](src: Map[String, Seq[String]], key: String, companion: GeneratedEnumCompanion[T]): Seq[T] =
      toStringValues(src, key).map(v => toEnumValueInternal(v, companion))

    private def toEnumValueInternal[T <: GeneratedEnum](value: String, companion: GeneratedEnumCompanion[T]): T =
      Try(value.toInt).toOption.map(companion.fromValue) match {
        case Some(value) => value
        case None        => companion.fromName(value).getOrElse(companion.fromValue(-1))
      }
  }
}
