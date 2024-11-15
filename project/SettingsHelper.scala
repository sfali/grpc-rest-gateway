import sbt.Keys.{
  allDependencies,
  description,
  homepage,
  name,
  normalizedName,
  organization,
  organizationName,
  sbtVersion,
  scalaVersion,
  startYear,
  version
}
import sbt.{Compile, Project}
import sbtbuildinfo.BuildInfoPlugin
import sbtbuildinfo.BuildInfoPlugin.autoImport.{BuildInfoKey, buildInfoKeys, buildInfoPackage}

object SettingsHelper {

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
        Compile / allDependencies
      )
    )
}
