package grpc_rest_gateway

import com.improving.grpc_rest_gateway.compiler.BuildInfo
import protocbridge.{Artifact, SandboxedJvmGenerator}
import scalapb.GeneratorOption
import scalapb.GeneratorOption.Scala3Sources

object gatewayGen {
  def apply(options: GeneratorOption*): (SandboxedJvmGenerator, Seq[String]) =
    (
      SandboxedJvmGenerator.forModule(
        "scala",
        Artifact(
          BuildInfo.organization,
          "grpc-rest-gateway-code-gen_2.12",
          BuildInfo.version
        ),
        "com.improving.grpc_rest_gateway.compiler.GatewayGenerator$",
        com.improving.grpc_rest_gateway.compiler.GatewayGenerator.suggestedDependencies
      ),
      options.map(_.toString)
    )

  def apply(options: Set[GeneratorOption]): (SandboxedJvmGenerator, Seq[String]) = apply(options.toSeq*)

  def apply(scala3Sources: Boolean = false): (SandboxedJvmGenerator, Seq[String]) = {
    val optionsBuilder = Set.newBuilder[GeneratorOption]
    if (scala3Sources) {
      optionsBuilder += Scala3Sources
    }
    apply(optionsBuilder.result())
  }
}
