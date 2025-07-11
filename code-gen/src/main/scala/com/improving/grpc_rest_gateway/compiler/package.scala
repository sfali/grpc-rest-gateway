package com.improving
package grpc_rest_gateway

import com.google.api.{AnnotationsProto, HttpRule}
import com.google.api.HttpRule.PatternCase
import com.google.protobuf.Descriptors.{MethodDescriptor, ServiceDescriptor}
import com.improving.grpc_rest_gateway.api.GrpcRestGatewayProto
import com.improving.grpc_rest_gateway.api.GrpcRestGatewayProto.StatusDescription

import scala.jdk.CollectionConverters.*

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

  def isMethodAllowed(patternCase: PatternCase): Boolean =
    (PatternCase.GET == patternCase) ||
      (PatternCase.PUT == patternCase) ||
      (PatternCase.POST == patternCase) ||
      (PatternCase.DELETE == patternCase) ||
      (PatternCase.PATCH == patternCase)

  def extractPaths(m: MethodDescriptor): Seq[(PatternCase, String, String)] = {
    val http = m.getOptions.getExtension(AnnotationsProto.http)
    extractPathInternal(http) +: http.getAdditionalBindingsList.asScala.toList.map(extractPathInternal)
  }

  def getProtoFileName(name: String): String = {
    val separatorIndex = name.indexOf(".")
    val nameWithoutExtension = if (separatorIndex >= 0) name.substring(0, separatorIndex) else name
    nameWithoutExtension.replaceAll("\\.", "-")
  }

  def getUnaryCallsWithHttpExtension(service: ServiceDescriptor): Seq[MethodDescriptor] =
    service.getMethods.asScala.toList.filter { m =>
      // only unary calls with http method specified
      !m.isClientStreaming && !m.isServerStreaming && m.getOptions.hasExtension(AnnotationsProto.http)
    }

  def getSuccessStatusCode(md: MethodDescriptor): Int = {
    getStatusDescriptions(md).head.getStatus
    val statusDescription =
      if (md.getOptions.hasExtension(GrpcRestGatewayProto.statuses)) {
        val statuses = md.getOptions.getExtension(GrpcRestGatewayProto.statuses)
        if (statuses.hasSuccessStatus) {
          statuses.getSuccessStatus
        } else StatusDescription.getDefaultInstance
      } else StatusDescription.getDefaultInstance

    val value = statusDescription.getStatus
    if (value <= 0) 200 else value
  }

  def getStatusDescriptions(md: MethodDescriptor): Seq[StatusDescription] = {
    lazy val defaultSuccessStatus =
      StatusDescription.newBuilder().setStatus(200).setDescription("successful operation").build()

    if (md.getOptions.hasExtension(GrpcRestGatewayProto.statuses)) {
      val statuses = md.getOptions.getExtension(GrpcRestGatewayProto.statuses)
      val successStatus =
        if (statuses.hasSuccessStatus) {
          statuses.getSuccessStatus
        } else defaultSuccessStatus

      successStatus +: statuses.getOtherStatusList.asScala.toList
    } else Seq(defaultSuccessStatus)
  }
}
