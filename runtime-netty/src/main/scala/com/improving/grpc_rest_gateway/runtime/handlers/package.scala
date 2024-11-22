package com.improving
package grpc_rest_gateway
package runtime

import io.grpc.Status.Code
import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.{
  DefaultFullHttpResponse,
  FullHttpResponse,
  HttpHeaderNames,
  HttpMessage,
  HttpResponseStatus,
  HttpUtil
}
import scalapb.{GeneratedEnum, GeneratedEnumCompanion}

import java.nio.charset.StandardCharsets
import scala.util.Try

package object handlers {

  val GRPC_HTTP_CODE_MAP: Map[Int, HttpResponseStatus] = Map(
    Code.OK.value() -> HttpResponseStatus.OK,
    Code.CANCELLED.value() -> HttpResponseStatus.GONE,
    Code.UNKNOWN.value() -> HttpResponseStatus.NOT_FOUND,
    Code.INVALID_ARGUMENT.value() -> HttpResponseStatus.BAD_REQUEST,
    Code.DEADLINE_EXCEEDED.value() -> HttpResponseStatus.GATEWAY_TIMEOUT,
    Code.NOT_FOUND.value() -> HttpResponseStatus.NOT_FOUND,
    Code.ALREADY_EXISTS.value() -> HttpResponseStatus.CONFLICT,
    Code.PERMISSION_DENIED.value() -> HttpResponseStatus.FORBIDDEN,
    Code.RESOURCE_EXHAUSTED.value() -> HttpResponseStatus.INSUFFICIENT_STORAGE,
    Code.FAILED_PRECONDITION.value() -> HttpResponseStatus.PRECONDITION_FAILED,
    Code.ABORTED.value() -> HttpResponseStatus.GONE,
    Code.OUT_OF_RANGE.value() -> HttpResponseStatus.BAD_REQUEST,
    Code.UNIMPLEMENTED.value() -> HttpResponseStatus.NOT_IMPLEMENTED,
    Code.INTERNAL.value() -> HttpResponseStatus.INTERNAL_SERVER_ERROR,
    Code.UNAVAILABLE.value() -> HttpResponseStatus.NOT_ACCEPTABLE,
    Code.DATA_LOSS.value() -> HttpResponseStatus.PARTIAL_CONTENT,
    Code.UNAUTHENTICATED.value() -> HttpResponseStatus.UNAUTHORIZED
  )

  def buildFullHttpResponse(
    requestMsg: HttpMessage,
    responseBody: String,
    responseStatus: HttpResponseStatus,
    responseContentType: String
  ): FullHttpResponse = {

    val buf = Unpooled.copiedBuffer(responseBody, StandardCharsets.UTF_8)

    val res = new DefaultFullHttpResponse(
      requestMsg.protocolVersion(),
      responseStatus,
      buf
    )

    res.headers().set(HttpHeaderNames.CONTENT_TYPE, responseContentType)

    HttpUtil.setContentLength(res, buf.readableBytes)
    HttpUtil.setKeepAlive(res, HttpUtil.isKeepAlive(requestMsg))
    res
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
