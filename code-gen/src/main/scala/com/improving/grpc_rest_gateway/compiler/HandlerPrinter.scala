package com.improving
package grpc_rest_gateway
package compiler

import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import com.improving.grpc_rest_gateway.compiler.utils.Formatter

trait HandlerPrinter {

  protected val content: String
  protected val outputFileName: String

  lazy val result: CodeGeneratorResponse.File = {
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setName(outputFileName)
    b.setContent(Formatter.format(content))
    b.build()
  }
}
