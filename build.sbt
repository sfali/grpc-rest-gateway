import SettingsHelper.*
import Dependencies.*
import ReleaseTransformations.*

lazy val `runtime-core` = (projectMatrix in file("runtime-core"))
  .configure(commonSettings)
  .configure(configureBuildInfo("com.improving.grpc_rest_gateway.runtime.core"))
  .defaultAxes()
  .settings(
    name := "grpc-rest-gateway-runtime-core",
    libraryDependencies ++= RuntimeCoreDependencies,
    scalacOptions ++= (if (isScala3.value) Seq("-source", "future", "-explain")
                       else Seq("-Xsource:3"))
  )
  .jvmPlatform(scalaVersions = Seq(V.Scala212, V.Scala213, V.Scala3))

lazy val `runtime-netty` = (projectMatrix in file("runtime-netty"))
  .configure(commonSettings)
  .configure(configureBuildInfo("com.improving.grpc_rest_gateway.runtime.netty"))
  .defaultAxes()
  .settings(
    name := "grpc-rest-gateway-runtime-netty",
    libraryDependencies ++= RuntimeDependencies,
    scalacOptions ++= (if (isScala3.value) Seq("-source", "future", "-explain")
                       else Seq("-Xsource:3"))
  )
  .jvmPlatform(scalaVersions = Seq(V.Scala212, V.Scala213, V.Scala3))
  .dependsOn(`runtime-core`)

lazy val `runtime-pekko` = (projectMatrix in file("runtime-pekko"))
  .configure(commonSettings)
  .configure(configureBuildInfo("com.improving.grpc_rest_gateway.runtime.pekko"))
  .defaultAxes()
  .settings(
    name := "grpc-rest-gateway-runtime-pekko",
    libraryDependencies ++= RuntimePekkoDependencies,
    scalacOptions ++= (if (isScala3.value) Seq("-source", "future", "-explain")
                       else Seq("-Xsource:3"))
  )
  .jvmPlatform(scalaVersions = Seq(V.Scala212, V.Scala213, V.Scala3))
  .dependsOn(`runtime-core`)

lazy val codeGen = (projectMatrix in file("code-gen"))
  .configure(commonSettings)
  .configure(configureBuildInfo("com.improving.grpc_rest_gateway.compiler"))
  .defaultAxes()
  .settings(
    name := "grpc-rest-gateway-code-gen",
    libraryDependencies ++= CodegenDependencies,
    libraryDependencies ++= {
      Seq(
        if (isScala3.value) "org.scalameta" %% "scalafmt-dynamic" % V.ScalaFmt cross CrossVersion.for3Use2_13
        else "org.scalameta" %% "scalafmt-dynamic" % V.ScalaFmt
      )
    },
    scalacOptions ++= (if (isScala3.value) Seq("-source", "future")
                       else Seq("-Xsource:3"))
  )
  .jvmPlatform(scalaVersions = Seq(V.Scala212, V.Scala213, V.Scala3))

lazy val codeGenJVM212 = codeGen.jvm(V.Scala212)

lazy val protocGenGrpcRestGatewayPlugin =
  protocGenProject("protoc-gen-grpc-rest-gateway-plugin", codeGenJVM212)
    .settings(assemblyOptions)
    .settings(
      Compile / mainClass := Some("com.improving.grpc_rest_gateway.compiler.GatewayGenerator"),
      scalaVersion := V.Scala212
    )

lazy val `e2e-core` = (projectMatrix in file("e2e-core"))
  .configure(commonSettings)
  .defaultAxes()
  .dependsOn(`runtime-core`)
  .settings(
    publish / skip := true,
    libraryDependencies ++= E2ECore,
    scalacOptions ++= (if (isScala3.value) Seq("-source", "future")
                       else Seq("-Xsource:3"))
  )
  .jvmPlatform(scalaVersions = Seq(V.Scala212, V.Scala213, V.Scala3))

lazy val `e2e-api` = (project in file("e2e-api"))
  .configure(commonSettings)
  .settings(publish / skip := true)

lazy val `e2e-netty` = (projectMatrix in file("e2e-netty"))
  .configure(commonSettings)
  .dependsOn(`runtime-netty`, `e2e-core`)
  .enablePlugins(LocalCodeGenPlugin)
  .defaultAxes()
  .customRow(
    scalaVersions = Seq(V.Scala212),
    axisValues = Seq(VirtualAxis.jvm),
    settings = Seq(
      Test / unmanagedSourceDirectories += (Test / scalaSource).value.getParentFile / "jvm-2.12",
      Compile / PB.targets ++= Seq(scalapb.gen() -> (Compile / sourceManaged).value / "scalapb")
    )
  )
  .customRow(
    scalaVersions = Seq(V.Scala213),
    axisValues = Seq(VirtualAxis.jvm),
    settings = Seq(
      Test / unmanagedSourceDirectories += (Test / scalaSource).value.getParentFile / "jvm-2.13",
      Compile / PB.targets ++= Seq(scalapb.gen(scala3Sources = true) -> (Compile / sourceManaged).value / "scalapb")
    )
  )
  .customRow(
    scalaVersions = Seq(V.Scala3),
    axisValues = Seq(VirtualAxis.jvm),
    settings = Seq(
      Test / unmanagedSourceDirectories += (Test / scalaSource).value.getParentFile / "jvm-3",
      Compile / PB.targets ++= Seq(scalapb.gen(scala3Sources = true) -> (Compile / sourceManaged).value / "scalapb")
    )
  )
  .settings(
    publish / skip := true,
    codeGenClasspath := (codeGenJVM212 / Compile / fullClasspath).value,
    libraryDependencies ++= E2ENettyDependencies,
    scalacOptions ++= (if (isScala3.value) Seq("-source", "future")
                       else Seq("-Xsource:3")),
    (Compile / PB.protoSources) += (`e2e-api` / baseDirectory).value / "src" / "main" / "protobuf",
    Compile / PB.targets := Seq(
      (
        genModule("com.improving.grpc_rest_gateway.compiler.GatewayGenerator$"),
        Seq("scala3_sources", "implementation_type:netty")
      ) -> (Compile / sourceManaged).value / "scalapb",
      genModule(
        "com.improving.grpc_rest_gateway.compiler.SwaggerGenerator$"
      ) -> (Compile / resourceDirectory).value / "specs"
    )
  )
  .jvmPlatform(scalaVersions = Seq(V.Scala212, V.Scala213, V.Scala3))

lazy val `e2e-pekko` = (projectMatrix in file("e2e-pekko"))
  .configure(commonSettings)
  .dependsOn(`runtime-pekko`, `e2e-core`)
  .enablePlugins(PekkoGrpcPlugin, LocalCodeGenPlugin)
  .defaultAxes()
  .customRow(
    scalaVersions = Seq(V.Scala212),
    axisValues = Seq(VirtualAxis.jvm),
    settings = Seq(
      Test / unmanagedSourceDirectories += (Test / scalaSource).value.getParentFile / "jvm-2.12"
    )
  )
  .customRow(
    scalaVersions = Seq(V.Scala213),
    axisValues = Seq(VirtualAxis.jvm),
    settings = Seq(
      Test / unmanagedSourceDirectories += (Test / scalaSource).value.getParentFile / "jvm-2.13"
    )
  )
  .customRow(
    scalaVersions = Seq(V.Scala3),
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
      (
        genModule("com.improving.grpc_rest_gateway.compiler.GatewayGenerator$"),
        Seq("implementation_type:pekko")
      ) ->
        crossTarget.value / "pekko-grpc" / "main",
      (
        genModule(
          "com.improving.grpc_rest_gateway.compiler.SwaggerGenerator$"
        ),
        Seq()
      ) -> (Compile / resourceDirectory).value / "specs"
    )
  )
  .jvmPlatform(scalaVersions = Seq(V.Scala212, V.Scala213, V.Scala3))

lazy val `grpc-rest-gateway` =
  project
    .configure(commonSettings)
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
        releaseStepCommand("test"),
        releaseStepCommand("nettyJVM212Test"),
        releaseStepCommand("nettyJVM213Test"),
        releaseStepCommand("nettyJVM3Test"),
        releaseStepCommand("pekkoJVM212Test"),
        releaseStepCommand("pekkoJVM213Test"),
        releaseStepCommand("pekkoJVM3Test"),
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

addCommandAlias("nettyJVM212Test", "e2e-nettyJVM2_12 / clean; e2e-nettyJVM2_12 / test")
addCommandAlias("nettyJVM213Test", "e2e-nettyJVM2_13 / clean; e2e-nettyJVM2_13 / test")
addCommandAlias("nettyJVM3Test", "e2e-nettyJVM3 / clean; e2e-nettyJVM3 / test")

addCommandAlias("pekkoJVM212Test", "e2e-pekkoJVM2_12 / clean; e2e-pekkoJVM2_12 / test")
addCommandAlias("pekkoJVM213Test", "e2e-pekkoJVM2_13 / clean; e2e-pekkoJVM2_13 / test")
addCommandAlias("pekkoJVM3Test", "e2e-pekkoJVM3 / clean; e2e-pekkoJVM3 / test")

addCommandAlias("nettyJVM212Run", "e2e-nettyJVM2_12 / run")
addCommandAlias("nettyJVM213Run", "e2e-nettyJVM2_13 / run")
addCommandAlias("nettyJVM3Run", "e2e-nettyJVM3 / run")

addCommandAlias("pekkoJVM212Run", "e2e-pekkoJVM2_12 / run")
addCommandAlias("pekkoJVM213Run", "e2e-pekkoJVM2_13 / run")
addCommandAlias("pekkoJVM3Run", "e2e-pekkoJVM3 / run")
