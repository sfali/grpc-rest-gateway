package grpc_rest_gateway

import com.improving.grpc_rest_gateway.compiler.BuildInfo
import grpc_rest_gateway.ImplementationType.Netty
import protocbridge.{Artifact, SandboxedJvmGenerator}
import scalapb.GeneratorOption.Scala3Sources

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

  /** Creates a gateway generator with the specified options.
    *
    * @param scala3Sources
    *   Whether to generate Scala 3 sources. It is used for using `*` as the wild card import instead of `_`. For Scala
    *   2.12 or later, set to true if the "-Xsource:3" scala compiler option is used. Always enabled if the project is
    *   using Scala 3
    * @param useScala3Features Whether to use Scala 3 features. It is used to use `using` and `given` instead of `implicits`.
    * @param implementationType
    *   The implementation type (Netty, Pekko, Akka)
    * @return
    *   A tuple of the generator and the options
    */
  def apply(
    scala3Sources: Boolean = false,
    useScala3Features: Boolean = false,
    implementationType: ImplementationType = Netty
  ): (SandboxedJvmGenerator, Seq[String]) = {
    val optionsBuilder = Set.newBuilder[String]
    if (scala3Sources || useScala3Features) {
      optionsBuilder += Scala3Sources.toString()
    }
    if (useScala3Features) {
      optionsBuilder += "use_scala3_features"
    }
    optionsBuilder += s"implementation_type:$implementationType"
    apply(optionsBuilder.result().toSeq*)
  }
}
