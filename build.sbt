import scalapb.compiler.Version.scalapbVersion

val Scala213 = "2.13.15"
val Scala212 = "2.12.20"

Global / onChangedBuildSource := ReloadOnSourceChanges
ThisBuild / organization := "com.alphasystem"
ThisBuild / scalaVersion := Scala213

lazy val core = (projectMatrix in file("core"))
  .defaultAxes()
  .settings(
    name := "grpc-rest-gateway-core"
  )
  .jvmPlatform(scalaVersions = Seq(Scala212, Scala213))

lazy val codeGen = (projectMatrix in file("code-gen"))
  .enablePlugins(BuildInfoPlugin)
  .defaultAxes()
  .settings(
    name := "grpc-rest-gateway-code-gen",
    buildInfoKeys := Seq[BuildInfoKey](name, organization, version, scalaVersion, sbtVersion, Compile / allDependencies),
    buildInfoPackage := "com.alphasystem.compiler",
    libraryDependencies ++= Seq(
      "com.thesamet.scalapb" %% "compilerplugin" % scalapbVersion,
      "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapbVersion
    )
  )
  .jvmPlatform(scalaVersions = Seq(Scala212, Scala213))

lazy val codeGenJVM212 = codeGen.jvm(Scala212)

lazy val protocGenGrpcRestGatewayPlugin = protocGenProject("protoc-gen-grpc-rest-gateway-plugin", codeGenJVM212)
  .settings(
    Compile / mainClass := Some("com.alphasystem.compiler.GatewayGenerator"),
    scalaVersion := Scala212
  )

lazy val e2e = (projectMatrix in file("e2e"))
  .dependsOn(core)
  .enablePlugins(LocalCodeGenPlugin)
  .defaultAxes()
  .settings(
    publish / skip := true,
    codeGenClasspath := (codeGenJVM212 / Compile / fullClasspath).value,
    libraryDependencies ++= Seq(
      "com.google.api.grpc" % "googleapis-common-protos" % "0.0.3" % "protobuf",
      "org.scalameta" %% "munit" % "1.0.0" % Test
    ),
    testFrameworks += new TestFramework("munit.Framework"),
    Compile / PB.targets := Seq(
      scalapb.gen() -> (Compile / sourceManaged).value / "scalapb",
      genModule("com.alphasystem.compiler.GatewayGenerator$") -> (Compile / sourceManaged).value / "scalapb"
    )
  )
  .jvmPlatform(scalaVersions = Seq(Scala212, Scala213))

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
      (codeGen.projectRefs ++ core.projectRefs)*
    )
