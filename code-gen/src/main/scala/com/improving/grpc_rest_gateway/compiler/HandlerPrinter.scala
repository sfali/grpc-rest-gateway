package com.improving
package grpc_rest_gateway
package compiler

import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse

trait HandlerPrinter {

  val result: CodeGeneratorResponse.File
}
