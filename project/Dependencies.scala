import sbt.*

object Dependencies {

  object V {
    val Akka = "2.7.0"
    val AkkaGrpc = "2.3.4"
    val AkkaHttp = "10.5.0"
    val CommonsIo = "2.21.0"
    val CommonProtos = "2.9.6-0"
    val GrpcJava: String = scalapb.compiler.Version.grpcJavaVersion
    val JavaActivation = "1.1.1"
    val Logback = "1.5.32"
    val Pekko = "1.1.2"
    val PekkoGrpc = "1.1.1"
    val PekkoHttp = "1.1.0"
    val Protobuf: String = scalapb.compiler.Version.protobufVersion
    val Scala213 = "2.13.18"
    val Scala212 = "2.12.21"
    val Scala3 = "3.8.2"
    val ScalaPb: String = scalapb.compiler.Version.scalapbVersion
    val ScalaPbJson = "0.12.2"
    val ScalaTest = "3.2.19"
    val Slf4j = "2.0.17"
    val Sttp = "3.11.0"
    val SwaggerUi = "5.32.0"
    val TypesafeConfig = "1.4.6"
  }

  val CodegenDependencies: Seq[ModuleID] = Seq(
    "com.thesamet.scalapb" %% "compilerplugin" % V.ScalaPb,
    "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % V.ScalaPb,
    "org.scalatest" %% "scalatest" % V.ScalaTest % Test
  )

  val ApiDependencies: Seq[ModuleID] = Seq(
    "com.google.protobuf" % "protobuf-java" % V.Protobuf,
    "com.thesamet.scalapb" %% "scalapb-runtime" % V.ScalaPb % "protobuf"
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

  val TestDependencies: Seq[ModuleID] = Seq(
    "org.scalatest" %% "scalatest" % V.ScalaTest % Test
  )

  val PekkoTestDependencies: Seq[ModuleID] = TestDependencies ++ Seq(
    "org.apache.pekko" %% "pekko-http-testkit" % V.PekkoHttp % Test,
    "org.apache.pekko" %% "pekko-testkit" % V.Pekko % Test
  )

  val RuntimePekkoDependencies: Seq[ModuleID] = Seq(
    "org.apache.pekko" %% "pekko-actor" % V.Pekko % "provided",
    "org.apache.pekko" %% "pekko-stream-typed" % V.Pekko % "provided",
    "org.apache.pekko" %% "pekko-http" % V.PekkoHttp % "provided"
  )

  val RuntimeAkkaDependencies: Seq[ModuleID] = Seq(
    "com.typesafe.akka" %% "akka-actor" % V.Akka % "provided",
    "com.typesafe.akka" %% "akka-stream-typed" % V.Akka % "provided"
  )

  val E2ECore: Seq[ModuleID] = Seq(
    "ch.qos.logback" % "logback-classic" % V.Logback,
    "com.softwaremill.sttp.client3" %% "core" % V.Sttp,
    "com.thesamet.scalapb" %% "compilerplugin" % V.ScalaPb
  )

  val E2ENettyDependencies: Seq[ModuleID] = Seq(
    "org.scalatest" %% "scalatest" % V.ScalaTest % Test
  )

  val E2EPekkoDependencies: Seq[ModuleID] = Seq(
    "org.apache.pekko" %% "pekko-actor" % V.Pekko,
    "org.apache.pekko" %% "pekko-actor-typed" % V.Pekko,
    "org.apache.pekko" %% "pekko-stream-typed" % V.Pekko,
    "org.apache.pekko" %% "pekko-http" % V.PekkoHttp,
    "org.apache.pekko" %% "pekko-grpc-runtime" % V.PekkoGrpc
  ) ++ TestDependencies

  val E2EAkkaDependencies: Seq[ModuleID] = Seq(
    "com.typesafe.akka" %% "akka-actor" % V.Akka,
    "com.typesafe.akka" %% "akka-actor-typed" % V.Akka,
    "com.typesafe.akka" %% "akka-stream-typed" % V.Akka,
    "com.typesafe.akka" %% "akka-http" % V.AkkaHttp,
    "com.lightbend.akka.grpc" %% "akka-grpc-runtime" % V.AkkaGrpc
  ) ++ TestDependencies
}