package tryp

import sbt._
import sbt.Keys._

import bintray._
import BintrayKeys._

import Types._

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

  def userProject(name: String) = {
    pluginSubProject(name).in(file("."))
      .settings(
        bintrayTekResolver,
        publishTo := None
      )
  }

  object TryplugDeps
  extends Deps
  {
    override def deps = super.deps ++ Map(
      "user-level" → userLevel
    )

    val huy = "com.hanhuy.sbt"
    val sdkName = "android-sdk-plugin"

    val userLevel = ids(
      pd(huy, sdkName, sdkVersion, s"pfn/$sdkName"),
      pd("tryp.sbt", s"tryp-$androidName", trypVersion, "tek/sbt-tryp",
        androidName)
    )
  }

  def deps: Deps = TryplugDeps

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
    val plugin = project in file(".")
    plugin.settings(basicPluginSettings)
  }

  def pluginSubProject(name: String) = {
    Project(name, file(name))
      .settings(basicPluginSettings: _*)
      .settings(deps(name): _*)
      .dependsOn(deps.refs(name): _*)
  }

  val scalaVersionSetting = scalaVersion := "2.11.7"

  def pluginVersionDefaults = List(
    propVersion(sdkVersion, "sdk", "1.5.1"),
    propVersion(protifyVersion, "protify", "1.1.4")
  )

  val homeDir = sys.env.get("HOME").map(d ⇒ new File(d))


  def bintrayPluginResolver(name: String) = {
    val u = url(s"https://dl.bintray.com/$name/sbt-plugins")
    val n = s"bintray-$name-sbt"
    resolvers += Resolver.url(n, u)(Resolver.ivyStylePatterns)
  }

  lazy val bintrayTekResolver = bintrayPluginResolver("tek")

  def propVersion(setting: SettingKey[String], name: String, alt: String) = {
    setting <<= setting or Def.setting(sys.props.getOrElse(name, alt))
  }
}
