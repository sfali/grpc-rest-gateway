addSbtPlugin("com.eed3si9n" % "sbt-projectmatrix" % "0.11.0")
addSbtPlugin("com.thesamet" % "sbt-protoc" % "1.0.8")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.4")
addSbtPlugin("com.thesamet" % "sbt-protoc-gen-project" % "0.1.8")
addSbtPlugin("org.apache.pekko" % "pekko-grpc-sbt-plugin" % "1.1.1")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.13.1")
addSbtPlugin("com.awwsmm.sbt" % "sbt-dependency-updater" % "0.4.0")
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.11.1")
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "3.12.2")
addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.3.1")
addSbtPlugin("io.github.sfali23" % "sbt-semver-release" % "0.5.3")
addDependencyTreePlugin

libraryDependencies ++= Seq("com.thesamet.scalapb" %% "compilerplugin" % "0.11.18")
resolvers += "Sonatype OSS" at "https://s01.oss.sonatype.org/content/groups/public/"
