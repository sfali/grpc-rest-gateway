package grpc_rest_gateway

import com.improving.grpc_rest_gateway.compiler.BuildInfo
import protocbridge.{Artifact, SandboxedJvmGenerator}
import scalapb.GeneratorOption

object openApiGen {
  def apply(options: GeneratorOption*): (SandboxedJvmGenerator, Seq[String]) =
    (
      SandboxedJvmGenerator.forModule(
        "scala",
        Artifact(
          BuildInfo.organization,
          s"${BuildInfo.name}_2.12",
          BuildInfo.version
        ),
        "com.improving.grpc_rest_gateway.compiler.OpenApiGenerator$",
        com.improving.grpc_rest_gateway.compiler.OpenApiGenerator.suggestedDependencies
      ),
      options.map(_.toString)
    )

  def apply(options: Set[GeneratorOption] = Set.empty): (SandboxedJvmGenerator, Seq[String]) = apply(options.toSeq*)
}
