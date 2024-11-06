package com.improving
package grpc_rest_gateway

import com.google.api.{AnnotationsProto, HttpRule}
import com.google.api.HttpRule.PatternCase
import com.google.protobuf.Descriptors.MethodDescriptor

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
}
