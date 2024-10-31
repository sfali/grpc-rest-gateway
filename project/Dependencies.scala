import sbt.*

object Dependencies {

  object V {
    val CommonsIo = "2.16.1"
    val CommonProtos = "2.9.6-0"
    val GrpcJava: String = scalapb.compiler.Version.grpcJavaVersion
    val JavaActivation = "1.1.1"
    val Munit = "1.0.2"
    val ScalaPb: String = scalapb.compiler.Version.scalapbVersion
    val ScalaPbJson = "0.12.1"
    val ScalaXml = "2.2.0"
    val Slf4j = "2.0.12"
    val SwaggerUi = "5.17.14"
  }

  val CodegenDependencies: Seq[ModuleID] = Seq(
    "com.thesamet.scalapb" %% "compilerplugin" % V.ScalaPb,
    "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % V.ScalaPb
  )

  val RuntimeDependencies: Seq[ModuleID] = Seq(
    "javax.activation" % "activation" % V.JavaActivation,
    "org.scala-lang.modules" %% "scala-xml" % V.ScalaXml,
    "commons-io" % "commons-io" % V.CommonsIo,
    "com.thesamet.scalapb" %% "compilerplugin" % V.ScalaPb,
    "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % V.ScalaPb,
    "io.grpc" % "grpc-netty" % V.GrpcJava,
    "com.thesamet.scalapb" %% "scalapb-json4s" % V.ScalaPbJson,
    "org.webjars" % "swagger-ui" % V.SwaggerUi,
    "com.thesamet.scalapb.common-protos" %% "proto-google-common-protos-scalapb_0.11" % V.CommonProtos % "protobuf",
    "com.thesamet.scalapb.common-protos" %% "proto-google-common-protos-scalapb_0.11" % V.CommonProtos,
    "org.slf4j" % "slf4j-api" % V.Slf4j
  )

  val E2EDependencies: Seq[ModuleID] = Seq(
    // "com.google.api.grpc" % "googleapis-common-protos" % "0.0.3" % "protobuf",
    "org.scalameta" %% "munit" % V.Munit % Test
  )
}
