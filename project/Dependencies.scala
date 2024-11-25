import sbt.*

object Dependencies {

  object V {
    val CommonsIo = "2.16.1"
    val CommonProtos = "2.9.6-0"
    val Coursier = "1.0.24"
    val GrpcJava: String = scalapb.compiler.Version.grpcJavaVersion
    val JavaActivation = "1.1.1"
    val Logback = "1.5.12"
    val Pekko = "1.1.2"
    val PekkoGrpc = "1.1.1"
    val PekkoHttp = "1.1.0"
    val ScalaFmt = "3.8.3"
    val ScalaPb: String = scalapb.compiler.Version.scalapbVersion
    val ScalaPbJson = "0.12.1"
    val ScalaTest = "3.2.19"
    val Slf4j = "2.0.12"
    val Sttp = "3.10.1"
    val SwaggerUi = "5.17.14"
    val TypesafeConfig = "1.4.3"
  }

  val CodegenDependencies: Seq[ModuleID] = Seq(
    "com.thesamet.scalapb" %% "compilerplugin" % V.ScalaPb,
    "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % V.ScalaPb,
    "org.scalameta" % "scalafmt-interfaces" % V.ScalaFmt,
    "io.get-coursier" % "interface" % V.Coursier,
    "xerces" % "xercesImpl" % "2.12.2"
  )

  val RuntimeCoreDependencies: Seq[ModuleID] = Seq(
    "io.grpc" % "grpc-api" % V.GrpcJava,
    "com.thesamet.scalapb" %% "compilerplugin" % V.ScalaPb,
    "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % V.ScalaPb,
    "com.thesamet.scalapb" %% "scalapb-json4s" % V.ScalaPbJson,
    "org.webjars" % "swagger-ui" % V.SwaggerUi,
    "com.typesafe" % "config" % V.TypesafeConfig,
    "org.slf4j" % "slf4j-api" % V.Slf4j,
    "javax.activation" % "activation" % V.JavaActivation,
    "commons-io" % "commons-io" % V.CommonsIo,
    "com.thesamet.scalapb.common-protos" %% "proto-google-common-protos-scalapb_0.11" % V.CommonProtos % "protobuf",
    "com.thesamet.scalapb.common-protos" %% "proto-google-common-protos-scalapb_0.11" % V.CommonProtos
  )

  val RuntimeDependencies: Seq[ModuleID] = Seq(
    "io.grpc" % "grpc-netty" % V.GrpcJava
  )

  val RuntimePekkoDependencies: Seq[ModuleID] = Seq(
    "org.apache.pekko" %% "pekko-actor" % V.Pekko % "provided",
    "org.apache.pekko" %% "pekko-stream-typed" % V.Pekko % "provided",
    "org.apache.pekko" %% "pekko-http" % V.PekkoHttp % "provided"
  )

  val E2EDependencies: Seq[ModuleID] = Seq(
    "ch.qos.logback" % "logback-classic" % V.Logback,
    "org.scalatest" %% "scalatest" % V.ScalaTest % Test,
    "com.softwaremill.sttp.client3" %% "core" % V.Sttp % Test
  )

  val E2EPekkoDependencies: Seq[ModuleID] = Seq(
    "org.apache.pekko" %% "pekko-actor" % V.Pekko,
    "org.apache.pekko" %% "pekko-actor-typed" % V.Pekko,
    "org.apache.pekko" %% "pekko-stream-typed" % V.Pekko,
    "org.apache.pekko" %% "pekko-http" % V.PekkoHttp,
    "org.apache.pekko" %% "pekko-grpc-runtime" % V.PekkoGrpc
  )
}
