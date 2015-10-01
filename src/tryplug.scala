package tryp

import sbt._
import sbt.Keys._

import bintray._
import BintrayKeys._

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
  val sdkVersion = settingKey[String]("android-sdk-plugin version")
  val protifyVersion = settingKey[String]("protify version")
}

trait Tryplug
{
  import TrypKeys._

  def plugin(org: String, name: String, version: SettingKey[String]) =
      libraryDependencies += Defaults.sbtPluginExtra(
        org % name % version.value,
        (sbtBinaryVersion in update).value,
        (scalaBinaryVersion in update).value
      )

  def androidName = "android"

  def trypOrg = "tryp.sbt"

  def trypPath = Env.localProject("sbt-tryp")

  def userProject = {
    val userLevel = project in file(".")
    Env.trypDebug map { _ ⇒
      userLevel dependsOn(ProjectRef(trypPath, androidName))
    } getOrElse {
      userLevel settings(
        // setting the resolver like this causes buggy duplication errors
        // invalidating publishTo circumvents those
        bintrayTekResolver,
        publishTo := None,
        plugin(trypOrg, s"tryp-$androidName", trypVersion in Global)
      )
    }
  }

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

  def commonBasicSettings: List[Setting[_]] = List(
    (updateOptions := updateOptions.value.withCachedResolution(true))
  ) ++ compilerSettings

  def basicSettings = commonBasicSettings ++ List(
    scalaVersionSetting
  )

  def basicPluginSettings = commonBasicSettings ++ List(
    organization := "tryp.sbt",
    sbtPlugin := true,
    scalaSource in Compile := baseDirectory.value / "src",
    licenses += ("MIT", url("http://opensource.org/licenses/MIT")),
    bintrayRepository in bintray := "sbt-plugins",
    bintrayOrganization in bintray := None
  )

  def pluginProject = {
    val plugin = (project in file("."))
    plugin.settings(basicPluginSettings)
  }

  val scalaVersionSetting = scalaVersion := "2.11.7"

  def pluginVersionDefaults = List(
    sdkVersion in Global := sys.props.getOrElse("sdk", "1.5.1"),
    protifyVersion in Global := sys.props.getOrElse("protify", "1.1.5")
  )

  val homeDir = sys.env.get("HOME").map(d ⇒ new File(d))


  def bintrayPluginResolver(name: String) = {
    val u = url(s"https://dl.bintray.com/$name/sbt-plugins")
    val n = s"bintray-$name-sbt"
    resolvers += Resolver.url(n, u)(Resolver.ivyStylePatterns)
  }

  lazy val bintrayTekResolver = bintrayPluginResolver("tek")
}
