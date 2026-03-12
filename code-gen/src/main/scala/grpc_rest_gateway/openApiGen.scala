package grpc_rest_gateway

import com.improving.grpc_rest_gateway.compiler.BuildInfo
import protocbridge.{Artifact, SandboxedJvmGenerator}

object openApiGen {
  def apply(options: String*): (SandboxedJvmGenerator, Seq[String]) =
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

  def apply(version: String = "0.1.0-SNAPSHOT"): (SandboxedJvmGenerator, Seq[String]) = {
    val optionsBuilder = Set.newBuilder[String]
    optionsBuilder += s"version:$version"
    apply(optionsBuilder.result().toSeq*)
  }
}
