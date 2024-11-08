addSbtPlugin("com.eed3si9n" % "sbt-projectmatrix" % "0.7.0")
addSbtPlugin("com.thesamet" % "sbt-protoc" % "1.0.7")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.6")
addSbtPlugin("com.thesamet" % "sbt-protoc-gen-project" % "0.1.6")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.10.0")
addSbtPlugin("com.awwsmm.sbt" % "sbt-dependency-updater" % "0.4.0")
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.10.4")
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "3.12.2")
addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.3.0")
addSbtPlugin("io.github.sfali23" % "sbt-semver-release" % "0.3.0")
addDependencyTreePlugin

libraryDependencies ++= Seq("com.thesamet.scalapb" %% "compilerplugin" % "0.11.15")
resolvers += "Sonatype OSS" at "https://s01.oss.sonatype.org/content/groups/public/"
