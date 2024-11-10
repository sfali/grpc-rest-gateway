package com.improving
package grpc_rest_gateway

import com.google.api.{AnnotationsProto, HttpRule}
import com.google.api.HttpRule.PatternCase
import com.google.protobuf.Descriptors.MethodDescriptor

import scala.jdk.CollectionConverters._

package object compiler {

  private def extractPathInternal(http: HttpRule) =
    http.getPatternCase match {
      case PatternCase.GET    => (PatternCase.GET, http.getGet, "")
      case PatternCase.PUT    => (PatternCase.PUT, http.getPut, http.getBody)
      case PatternCase.POST   => (PatternCase.POST, http.getPost, http.getBody)
      case PatternCase.DELETE => (PatternCase.DELETE, http.getDelete, "")
      case PatternCase.PATCH  => (PatternCase.PATCH, http.getPatch, "")
      case _                  => (PatternCase.PATTERN_NOT_SET, "", "")
    }

  def extractPaths(m: MethodDescriptor): Seq[(PatternCase, String, String)] = {
    val http = m.getOptions.getExtension(AnnotationsProto.http)
    extractPathInternal(http) +: http.getAdditionalBindingsList.asScala.map(extractPathInternal).toSeq
  }

}
