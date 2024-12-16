package com.improving
package grpc_rest_gateway
package compiler

import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse

trait HandlerPrinter {

  protected val content: String
  protected val outputFileName: String

  lazy val result: CodeGeneratorResponse.File = {
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setName(outputFileName)
    b.setContent(content)
    b.build()
  }
}
