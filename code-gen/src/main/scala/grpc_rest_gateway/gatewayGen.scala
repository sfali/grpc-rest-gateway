package grpc_rest_gateway

import com.improving.grpc_rest_gateway.compiler.BuildInfo
import grpc_rest_gateway.ImplementationType.Netty
import protocbridge.{Artifact, SandboxedJvmGenerator}
import scalapb.GeneratorOption.{FlatPackage, Scala3Sources}

object gatewayGen {
  def apply(options: String*): (SandboxedJvmGenerator, Seq[String]) =
    (
      SandboxedJvmGenerator.forModule(
        "scala",
        Artifact(
          BuildInfo.organization,
          s"${BuildInfo.name}_2.12",
          BuildInfo.version
        ),
        "com.improving.grpc_rest_gateway.compiler.GatewayGenerator$",
        com.improving.grpc_rest_gateway.compiler.GatewayGenerator.suggestedDependencies
      ),
      options
    )

  def apply(options: Set[String]): (SandboxedJvmGenerator, Seq[String]) = apply(options.toSeq*)

  def apply(
    flatPackage: Boolean = false,
    scala3Sources: Boolean = false,
    implementationType: ImplementationType = Netty
  ): (SandboxedJvmGenerator, Seq[String]) = {
    val optionsBuilder = Set.newBuilder[String]
    if (flatPackage) {
      optionsBuilder += FlatPackage.toString()
    }
    if (scala3Sources) {
      optionsBuilder += Scala3Sources.toString()
    }
    optionsBuilder += s"implementation_type:$implementationType"
    apply(optionsBuilder.result())
  }
}
