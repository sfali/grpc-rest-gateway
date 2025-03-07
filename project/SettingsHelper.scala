import Dependencies.V.Scala213
import com.awwsmm.sbt.DependencyUpdaterPlugin
import org.scalafmt.sbt.ScalafmtPlugin
import sbt.{Compile, Def, Global, Project, ThisBuild, url}
import sbt.Keys.*
import sbt.librarymanagement.ivy.Credentials
import sbt.librarymanagement.{CrossVersion, Developer, ScmInfo}
import sbt.nio.Keys.{ReloadOnSourceChanges, onChangedBuildSource}
import sbtassembly.AssemblyKeys.assemblyMergeStrategy
import sbtassembly.AssemblyPlugin.autoImport.assembly
import sbtassembly.{MergeStrategy, PathList}
import sbtbuildinfo.BuildInfoPlugin
import sbtbuildinfo.BuildInfoPlugin.autoImport.{BuildInfoKey, buildInfoKeys, buildInfoPackage}
import xerial.sbt.Sonatype.*
import xerial.sbt.Sonatype.autoImport.*

object SettingsHelper {

  def isScala3: Def.Initialize[Boolean] = Def.setting[Boolean](scalaVersion.value.startsWith("3."))

  def commonSettings(project: Project): Project =
    project
      .enablePlugins(ScalafmtPlugin, DependencyUpdaterPlugin)
      .settings(
        Global / onChangedBuildSource := ReloadOnSourceChanges,
        ThisBuild / organization := "io.github.sfali23",
        ThisBuild / scalaVersion := Scala213,
        ThisBuild / versionScheme := Some("semver-spec"),
        ThisBuild / sonatypeRepository := "https://s01.oss.sonatype.org/service/local",
        ThisBuild / sonatypeCredentialHost := "s01.oss.sonatype.org",
        ThisBuild / credentials += Credentials(
          realm = "Sonatype Nexus Repository Manager",
          host = "s01.oss.sonatype.org",
          userName = System.getenv("SONATYPE_USERNAME"),
          passwd = System.getenv("SONATYPE_PASSWORD")
        ),
        ThisBuild / publishTo := sonatypePublishToBundle.value,
        ThisBuild / sonatypeProjectHosting := Some(
          GitHubHosting(
            "sfali",
            "grpc-rest-gateway",
            "syed.f.ali@improving.com"
          )
        ),
        ThisBuild / developers := List(
          Developer(
            id = "sfali",
            name = "Syed Farhan Ali",
            email = "syed.f.ali@improving.com",
            url = url("https://github.com/sfali/grpc-rest-gateway")
          )
        ),
        ThisBuild / licenses := Seq(
          "APL2" -> url("https://www.apache.org/licenses/LICENSE-2.0.txt")
        ),
        ThisBuild / homepage := Some(url("https://github.com/sfali/grpc-rest-gateway")),
        ThisBuild / scmInfo := Some(
          ScmInfo(
            url("https://github.com/sfali/grpc-rest-gateway"),
            "scm:git@github.com:sfali/grpc-rest-gateway.git"
          )
        )
      )

  def configureBuildInfo(packageName: String)(project: Project): Project = project
    .enablePlugins(BuildInfoPlugin)
    .settings(
      buildInfoPackage := packageName,
      buildInfoKeys := Seq[BuildInfoKey](
        name,
        normalizedName,
        description,
        homepage,
        startYear,
        organization,
        organizationName,
        version,
        scalaVersion,
        sbtVersion,
        "scalaPartialVersion" -> CrossVersion.partialVersion(scalaVersion.value),
        "swaggerUiVersion" -> Dependencies.V.SwaggerUi,
        Compile / allDependencies
      )
    )

  def assemblyOptions: Seq[Def.Setting[String => MergeStrategy]] = Seq(
    assembly / assemblyMergeStrategy := {
      case PathList("scala", "annotation", "nowarn.class" | "nowarn$.class") => MergeStrategy.first
      case PathList("scala", "util", _*)                                     => MergeStrategy.first
      case PathList("scala-collection-compat.properties")                    => MergeStrategy.first
      case x => (assembly / assemblyMergeStrategy).value.apply(x)
    }
  )
}
