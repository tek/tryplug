package tryp

import sbt._
import sbt.Keys._

object Types
{
  type DepSpec = Setting[Seq[ModuleID]]
  type Setts = Seq[Setting[_]]
}
import Types._

object Env
{
  val current = sys.props.getOrElse("env", "development")

  def development = current == "development"

  val projectBaseEnvVar = "TRYP_SCALA_PROJECT_DIR"

  val projectBaseProp = "tryp.projectsdir"

  val projectBasePath = sys.props.getOrElse(
    projectBaseProp,
    sys.env.get(projectBaseEnvVar)
      .getOrElse(sys.error(
        s"Need to pass -D$projectBaseProp or set $$$projectBaseEnvVar"))
  )

  lazy val projectBase = new File(projectBasePath)

  def cloneRepo(path: String, dirname: String) = {
    s"hub clone $path ${Env.projectBase}/$dirname" !
  }

  def localProject(path: String) = {
    val dirname = path.split("/").last
    val localPath = Env.projectBase / dirname
    if (!localPath.isDirectory) cloneRepo(path, dirname)
    localPath
  }

  def trypDebug = sys.env.get("TRYP_DEBUG")
}

object TrypKeys
{
  val trypVersion = settingKey[String]("tryp-build version")
  val tryplugVersion = settingKey[String]("tryplug version")
}

trait Tryplug
{
  def plugin(org: String, name: String, version: SettingKey[String]) =
      libraryDependencies += Defaults.sbtPluginExtra(
        org % name % version.value,
        (sbtBinaryVersion in update).value,
        (scalaBinaryVersion in update).value
      )

  def devdep(org: String, name: String, version: SettingKey[String]) = {
      if (Env.development) Seq()
      else Seq(plugin(org, name, version))
  }

  def compilerSettings = Seq(
    scalacOptions ++= Seq(
      "-feature",
      "-deprecation",
      "-unchecked",
      "-language:implicitConversions",
      "-language:postfixOps",
      "-language:reflectiveCalls",
      "-language:experimental.macros",
      "-language:existentials",
      "-language:higherKinds"
    )
  )

  lazy val bintrayTekResolver = {
    val name = "bintray-tek-sbt"
    val link = url("https://dl.bintray.com/tek/sbt-plugins")
    Resolver.url(name, link)(Resolver.ivyStylePatterns)
  }
}
