import SettingsHelper.*
import Dependencies.*
import ReleaseTransformations.*
import sbt.Def
import xerial.sbt.Sonatype.*

val Scala213 = "2.13.15"
val Scala212 = "2.12.20"
val Scala3 = "3.5.2"

def isScala3: Def.Initialize[Boolean] = Def.setting[Boolean](scalaVersion.value.startsWith("3."))

Global / onChangedBuildSource := ReloadOnSourceChanges
ThisBuild / organization := "io.github.sfali23"
ThisBuild / scalaVersion := Scala213
ThisBuild / sonatypeRepository := "https://s01.oss.sonatype.org/service/local"
ThisBuild / sonatypeCredentialHost := "s01.oss.sonatype.org"
ThisBuild / credentials += Credentials(Path.userHome / ".sbt" / "sonatype-credentials")
ThisBuild / publishTo := sonatypePublishToBundle.value
ThisBuild / sonatypeProjectHosting := Some(
  GitHubHosting(
    "sfali",
    "grpc-rest-gateway",
    "syed.f.ali@improving.com"
  )
)
ThisBuild / developers := List(
  Developer(
    id = "sfali",
    name = "Syed Farhan Ali",
    email = "syed.f.ali@improving.com",
    url = url("https://github.com/sfali/grpc-rest-gateway")
  )
)
ThisBuild / licenses := Seq(
  "APL2" -> url("https://www.apache.org/licenses/LICENSE-2.0.txt")
)
ThisBuild / homepage := Some(url("https://github.com/sfali/grpc-rest-gateway"))

ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/sfali/grpc-rest-gateway"),
    "scm:git@github.com:sfali/grpc-rest-gateway.git"
  )
)

lazy val `runtime-core` = (projectMatrix in file("runtime-core"))
  .configure(configureBuildInfo("com.improving.grpc_rest_gateway.runtime.core"))
  .enablePlugins(ScalafmtPlugin)
  .defaultAxes()
  .settings(
    name := "grpc-rest-gateway-runtime-core",
    libraryDependencies ++= RuntimeCoreDependencies,
    scalacOptions ++= (if (isScala3.value) Seq("-source", "future", "-explain")
                       else Seq("-Xsource:3"))
  )
  .jvmPlatform(scalaVersions = Seq(Scala212, Scala213, Scala3))

lazy val `runtime-netty` = (projectMatrix in file("runtime-netty"))
  .configure(configureBuildInfo("com.improving.grpc_rest_gateway.runtime.netty"))
  .enablePlugins(ScalafmtPlugin)
  .defaultAxes()
  .settings(
    name := "grpc-rest-gateway-runtime-netty",
    libraryDependencies ++= RuntimeDependencies,
    scalacOptions ++= (if (isScala3.value) Seq("-source", "future", "-explain")
                       else Seq("-Xsource:3"))
  )
  .jvmPlatform(scalaVersions = Seq(Scala212, Scala213, Scala3))
  .dependsOn(`runtime-core`)

lazy val `runtime-pekko` = (projectMatrix in file("runtime-pekko"))
  .configure(configureBuildInfo("com.improving.grpc_rest_gateway.runtime.pekko"))
  .enablePlugins(ScalafmtPlugin)
  .defaultAxes()
  .settings(
    name := "grpc-rest-gateway-runtime-pekko",
    libraryDependencies ++= RuntimePekkoDependencies,
    scalacOptions ++= (if (isScala3.value) Seq("-source", "future", "-explain")
                       else Seq("-Xsource:3"))
  )
  .jvmPlatform(scalaVersions = Seq(Scala212, Scala213, Scala3))
  .dependsOn(`runtime-core`)

lazy val codeGen = (projectMatrix in file("code-gen"))
  .configure(configureBuildInfo("com.improving.grpc_rest_gateway.compiler"))
  .enablePlugins(ScalafmtPlugin)
  .defaultAxes()
  .settings(
    name := "grpc-rest-gateway-code-gen",
    buildInfoKeys := Seq[BuildInfoKey](name, organization, version, scalaVersion, sbtVersion, Compile / allDependencies),
    libraryDependencies ++= CodegenDependencies,
    scalacOptions ++= (if (isScala3.value) Seq("-source", "future")
                       else Seq("-Xsource:3"))
  )
  .jvmPlatform(scalaVersions = Seq(Scala212, Scala213, Scala3))

lazy val codeGenJVM212 = codeGen.jvm(Scala212)

lazy val protocGenGrpcRestGatewayPlugin = protocGenProject("protoc-gen-grpc-rest-gateway-plugin", codeGenJVM212)
  .settings(
    Compile / mainClass := Some("com.improving.grpc_rest_gateway.compiler.GatewayGenerator"),
    scalaVersion := Scala212
  )

lazy val `e2e-api` = project
  .in(file("e2e-api"))
  .settings(
    publish / skip := true
  )

lazy val e2e = (projectMatrix in file("e2e"))
  .dependsOn(`runtime-netty`)
  .enablePlugins(LocalCodeGenPlugin, ScalafmtPlugin)
  .defaultAxes()
  .customRow(
    scalaVersions = Seq(Scala212),
    axisValues = Seq(VirtualAxis.jvm),
    settings = Seq(
      Test / unmanagedSourceDirectories += (Test / scalaSource).value.getParentFile / "jvm-2.12",
      Compile / PB.targets ++= Seq(scalapb.gen() -> (Compile / sourceManaged).value / "scalapb")
    )
  )
  .customRow(
    scalaVersions = Seq(Scala213),
    axisValues = Seq(VirtualAxis.jvm),
    settings = Seq(
      Test / unmanagedSourceDirectories += (Test / scalaSource).value.getParentFile / "jvm-2.13",
      Compile / PB.targets ++= Seq(scalapb.gen(scala3Sources = true) -> (Compile / sourceManaged).value / "scalapb")
    )
  )
  .customRow(
    scalaVersions = Seq(Scala3),
    axisValues = Seq(VirtualAxis.jvm),
    settings = Seq(
      Test / unmanagedSourceDirectories += (Test / scalaSource).value.getParentFile / "jvm-3",
      Compile / PB.targets ++= Seq(scalapb.gen(scala3Sources = true) -> (Compile / sourceManaged).value / "scalapb")
    )
  )
  .settings(
    publish / skip := true,
    codeGenClasspath := (codeGenJVM212 / Compile / fullClasspath).value,
    libraryDependencies ++= E2EDependencies,
    scalacOptions ++= (if (isScala3.value) Seq("-source", "future")
                       else Seq("-Xsource:3")),
    (Compile / PB.protoSources) += (`e2e-api` / baseDirectory).value / "src" / "main" / "protobuf",
    Compile / PB.targets := Seq(
      (
        genModule("com.improving.grpc_rest_gateway.compiler.GatewayGenerator$"),
        Seq("scala3_sources")
      ) -> (Compile / sourceManaged).value / "scalapb",
      genModule(
        "com.improving.grpc_rest_gateway.compiler.SwaggerGenerator$"
      ) -> (Compile / resourceDirectory).value / "specs"
    )
  )
  .jvmPlatform(scalaVersions = Seq(Scala212, Scala213, Scala3))

lazy val `e2e-pekko` = (projectMatrix in file("e2e-pekko"))
  .dependsOn(`runtime-pekko`)
  .enablePlugins(PekkoGrpcPlugin, LocalCodeGenPlugin, ScalafmtPlugin)
  .defaultAxes()
  .customRow(
    scalaVersions = Seq(Scala212),
    axisValues = Seq(VirtualAxis.jvm),
    settings = Seq(
      Test / unmanagedSourceDirectories += (Test / scalaSource).value.getParentFile / "jvm-2.12"
    )
  )
  .customRow(
    scalaVersions = Seq(Scala213),
    axisValues = Seq(VirtualAxis.jvm),
    settings = Seq(
      Test / unmanagedSourceDirectories += (Test / scalaSource).value.getParentFile / "jvm-2.13"
    )
  )
  .customRow(
    scalaVersions = Seq(Scala3),
    axisValues = Seq(VirtualAxis.jvm),
    settings = Seq(
      Test / unmanagedSourceDirectories += (Test / scalaSource).value.getParentFile / "jvm-3"
    )
  )
  .settings(
    publish / skip := true,
    codeGenClasspath := (codeGenJVM212 / Compile / fullClasspath).value,
    libraryDependencies ++= E2EPekkoDependencies,
    /*scalacOptions ++= (if (isScala3.value) Seq("-source", "future")
                       else Seq("-Xsource:3")),*/
    (Compile / PB.protoSources) += (`e2e-api` / baseDirectory).value / "src" / "main" / "protobuf",
    pekkoGrpcGeneratedLanguages := Seq(PekkoGrpc.Scala),
    pekkoGrpcGeneratedSources := Seq(PekkoGrpc.Client, PekkoGrpc.Server),
    pekkoGrpcCodeGeneratorSettings := Seq("grpc", "single_line_to_proto_string"),
    scalacOptions ++= Seq(
      "-Wconf:src=pekko-grpc/.*:silent" // Ignore warnings in classes generated by pekko-grpc
    ),
    Compile / PB.targets ++= Seq(
      genModule("com.improving.grpc_rest_gateway.compiler.PekkoGatewayGenerator$") ->
        crossTarget.value / "pekko-grpc" / "main",
      genModule(
        "com.improving.grpc_rest_gateway.compiler.SwaggerGenerator$"
      ) -> (Compile / resourceDirectory).value / "specs"
    )
  )
  .jvmPlatform(scalaVersions = Seq(Scala212, Scala213, Scala3))

lazy val `grpc-rest-gateway` =
  project
    .in(file("."))
    .enablePlugins(DependencyUpdaterPlugin)
    .settings(
      startingVersion := "0.2.0",
      publishMavenStyle := true,
      publishArtifact := false,
      publish := {},
      publishLocal := {},
      releaseProcess := Seq[ReleaseStep](
        checkSnapshotDependencies,
        inquireVersions,
        runClean,
        releaseStepCommand("nettyJVM212Test"),
        releaseStepCommand("nettyJVM213Test"),
        releaseStepCommand("nettyJVM3Test"),
        setReleaseVersion,
        tagRelease,
        publishArtifacts,
        releaseStepCommand("publishSigned"),
        releaseStepCommand("sonatypeBundleRelease"),
        pushChanges
      )
    )
    .aggregate(protocGenGrpcRestGatewayPlugin.agg)
    .aggregate(
      (codeGen.projectRefs ++ `runtime-core`.projectRefs ++ `runtime-netty`.projectRefs ++ `runtime-pekko`.projectRefs)*
    )

addCommandAlias("nettyJVM212Test", "e2eJVM2_12 / clean; e2eJVM2_12 / test")
addCommandAlias("nettyJVM213Test", "e2eJVM2_13 / clean; e2eJVM2_13 / test")
addCommandAlias("nettyJVM3Test", "e2eJVM3 / clean; e2eJVM3 / test")

addCommandAlias("pekkoJVM212Test", "e2e-pekkoJVM2_12 / clean; e2e-pekkoJVM2_12 / compile")
addCommandAlias("pekkoJVM213Test", "e2e-pekkoJVM2_13 / clean; e2e-pekkoJVM2_13 / compile")
addCommandAlias("pekkoJVM3Test", "e2e-pekkoJVM3 / clean; e2e-pekkoJVM3 / compile")

addCommandAlias("nettyJVM212Run", "e2eJVM2_12 / run")
addCommandAlias("nettyJVM213Run", "e2eJVM2_13 / run")
addCommandAlias("nettyJVM3Run", "e2eJVM3 / run")
