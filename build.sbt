import Dependencies.*

val Scala213 = "2.13.15"
val Scala212 = "2.12.20"

Global / onChangedBuildSource := ReloadOnSourceChanges
ThisBuild / organization := "com.improving"
ThisBuild / scalaVersion := Scala213

lazy val runtime = (projectMatrix in file("runtime"))
  .defaultAxes()
  .settings(
    name := "grpc-rest-gateway-runtime",
    libraryDependencies ++= RuntimeDependencies
  )
  .jvmPlatform(scalaVersions = Seq(Scala212, Scala213))

lazy val codeGen = (projectMatrix in file("code-gen"))
  .enablePlugins(BuildInfoPlugin)
  .defaultAxes()
  .settings(
    name := "grpc-rest-gateway-code-gen",
    buildInfoKeys := Seq[BuildInfoKey](name, organization, version, scalaVersion, sbtVersion, Compile / allDependencies),
    buildInfoPackage := "com.improving.grpc_rest_gateway.compiler",
    libraryDependencies ++= CodegenDependencies
  )
  .jvmPlatform(scalaVersions = Seq(Scala212, Scala213))

lazy val codeGenJVM212 = codeGen.jvm(Scala212)

lazy val protocGenGrpcRestGatewayPlugin = protocGenProject("protoc-gen-grpc-rest-gateway-plugin", codeGenJVM212)
  .settings(
    Compile / mainClass := Some("com.improving.grpc_rest_gateway.compiler.GatewayGenerator"),
    scalaVersion := Scala212
  )

lazy val e2e = (project in file("e2e"))
  .dependsOn(runtime.projectRefs.last)
  .enablePlugins(LocalCodeGenPlugin)
  // .defaultAxes()
  .settings(
    publish / skip := true,
    codeGenClasspath := (codeGenJVM212 / Compile / fullClasspath).value,
    libraryDependencies ++= E2EDependencies,
    Compile / PB.targets := Seq(
      scalapb.gen() -> (Compile / sourceManaged).value / "scalapb",
      genModule(
        "com.improving.grpc_rest_gateway.compiler.GatewayGenerator$"
      ) -> (Compile / sourceManaged).value / "scalapb",
      genModule(
        "com.improving.grpc_rest_gateway.compiler.SwaggerGenerator$"
      ) -> (Compile / resourceDirectory).value / "specs"
    )
  )
//.jvmPlatform(scalaVersions = Seq(Scala212, Scala213))

lazy val `grpc-rest-gateway` =
  project
    .in(file("."))
    .settings(
      publishArtifact := false,
      publish := {},
      publishLocal := {}
    )
    .aggregate(protocGenGrpcRestGatewayPlugin.agg)
    .aggregate(
      (codeGen.projectRefs ++ runtime.projectRefs)*
    )
