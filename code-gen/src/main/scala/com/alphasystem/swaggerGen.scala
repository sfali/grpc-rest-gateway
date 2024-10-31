package com.alphasystem

import com.alphasystem.compiler.BuildInfo
import protocbridge.{Artifact, SandboxedJvmGenerator}
import scalapb.GeneratorOption

object swaggerGen {
  def apply(options: GeneratorOption*): (SandboxedJvmGenerator, Seq[String]) =
    (
      SandboxedJvmGenerator.forModule(
        "scala",
        Artifact(
          BuildInfo.organization,
          "codegen_2.12",
          BuildInfo.version
        ),
        "com.alphasystem.compiler.SwaggerGenerator$",
        com.alphasystem.compiler.SwaggerGenerator.suggestedDependencies
      ),
      options.map(_.toString)
    )

  def apply(options: Set[GeneratorOption] = Set.empty): (SandboxedJvmGenerator, Seq[String]) = apply(options.toSeq: _*)
}
