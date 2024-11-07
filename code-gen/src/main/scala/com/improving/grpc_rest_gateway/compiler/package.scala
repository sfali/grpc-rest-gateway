package com.improving
package grpc_rest_gateway

import com.google.api.{AnnotationsProto, HttpRule}
import com.google.api.HttpRule.PatternCase
import com.google.protobuf.Descriptors.MethodDescriptor

import scala.jdk.CollectionConverters._

package object compiler {

  def extractPath(m: MethodDescriptor): String = extractPath(m.getOptions.getExtension(AnnotationsProto.http))

  def extractPath(http: HttpRule): String =
    http.getPatternCase match {
      case PatternCase.GET    => http.getGet
      case PatternCase.PUT    => http.getPut
      case PatternCase.POST   => http.getPost
      case PatternCase.DELETE => http.getDelete
      case PatternCase.PATCH  => http.getPatch
      case _                  => ""
    }

  private def extractPathInternal(http: HttpRule) =
    http.getPatternCase match {
      case PatternCase.GET    => ("GET", http.getGet)
      case PatternCase.PUT    => ("PUT", http.getPut)
      case PatternCase.POST   => ("POST", http.getPost)
      case PatternCase.DELETE => ("DELETE", http.getDelete)
      case PatternCase.PATCH  => ("PATCH", http.getPatch)
      case _                  => ("", "")
    }

  def extractPaths(m: MethodDescriptor): Seq[(String, String)] = {
    val http = m.getOptions.getExtension(AnnotationsProto.http)
    extractPathInternal(http) +: http.getAdditionalBindingsList.asScala.map(extractPathInternal)
  }
}
