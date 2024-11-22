import sbt.*

object Dependencies {

  object V {
    val CommonsIo = "2.16.1"
    val CommonProtos = "2.9.6-0"
    val GrpcJava: String = scalapb.compiler.Version.grpcJavaVersion
    val JavaActivation = "1.1.1"
    val Logback = "1.5.12"
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
    "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % V.ScalaPb
  )

  val RuntimeCoreDependencies: Seq[ModuleID] = Seq(
    "io.grpc" % "grpc-api" % V.GrpcJava,
    "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % V.ScalaPb,
    "com.thesamet.scalapb" %% "scalapb-json4s" % V.ScalaPbJson,
    "org.webjars" % "swagger-ui" % V.SwaggerUi,
    "com.typesafe" % "config" % V.TypesafeConfig,
    "org.slf4j" % "slf4j-api" % V.Slf4j
  )

  val RuntimeDependencies: Seq[ModuleID] = Seq(
    "javax.activation" % "activation" % V.JavaActivation,
    "commons-io" % "commons-io" % V.CommonsIo,
    "com.thesamet.scalapb" %% "compilerplugin" % V.ScalaPb,
    "io.grpc" % "grpc-netty" % V.GrpcJava,
    "com.thesamet.scalapb.common-protos" %% "proto-google-common-protos-scalapb_0.11" % V.CommonProtos % "protobuf",
    "com.thesamet.scalapb.common-protos" %% "proto-google-common-protos-scalapb_0.11" % V.CommonProtos
  )

  val E2EDependencies: Seq[ModuleID] = Seq(
    "ch.qos.logback" % "logback-classic" % V.Logback,
    "org.scalatest" %% "scalatest" % V.ScalaTest % Test,
    "com.softwaremill.sttp.client3" %% "core" % V.Sttp % Test
  )
}
