package tryp

import sbt._
import sbt.Keys._

import bintray._
import BintrayKeys._

import Types._

object TrypKeys
{
  val Tryp = config("tryp")
  val trypVersion = settingKey[String]("tryp-build version") in Tryp
  val tryplugVersion = settingKey[String]("tryplug version") in Tryp
  val sdkVersion = settingKey[String]("android-sdk-plugin version") in Tryp
  val protifyVersion = settingKey[String]("protify version") in Tryp
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

  def userLevelName = "user-level"

  object TryplugDeps
  extends Deps
  {
    override def deps = super.deps ++ Map(
      userLevelName → userLevel
    )

    val huy = "com.hanhuy.sbt"
    val sdkName = "android-sdk-plugin"
    val protifyName = "protify"

    val userLevel = ids(
      pd(huy, sdkName, sdkVersion, "pfn", s"pfn/$sdkName"),
      pd(huy, s"android-$protifyName", protifyVersion, "pfn",
        s"pfn/$protifyName"),
      pd(trypOrg, s"tryp-$androidName", trypVersion, "tek", "tek/sbt-tryp",
        androidName),
      pd(trypOrg, "tryplug", tryplugVersion, "tek", "tek/tryplug",
        "tryplug", "macros")
    )
  }

  def deps: Deps = TryplugDeps

  def compilerSettings = List(
    scalacOptions ++= List(
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

  def trypSettings = commonBasicSettings ++ List(
    organization := "tryp",
    scalaVersionSetting
  )

  def trypPluginSettings = commonBasicSettings ++ List(
    organization := "tryp.sbt",
    sbtPlugin := true,
    scalaSource in Compile := baseDirectory.value / "src",
    licenses += ("MIT", url("http://opensource.org/licenses/MIT")),
    bintrayRepository in bintray := "sbt-plugins",
    bintrayOrganization in bintray := None
  )

  def pluginSubProject(name: String) = {
    Project(name, file(name))
      .settings(trypPluginSettings: _*)
      .settings(deps(name): _*)
      .settings(deps.pluginVersions(name): _*)
      .settings(VersionUpdateKeys.autoUpdateVersions := true)
      .dependsOn(deps.refs(name): _*)
  }

  def pluginProject(name: String) = {
    pluginSubProject(name).in(file("."))
      .settings(
        bintrayTekResolver,
        publishTo := None
      )
  }

  def userLevelDebugDeps = {
    Project(userLevelName, file("."))
      .settings(pluginVersionDefaults: _*)
      .dependsOn(deps.refs(userLevelName): _*)
  }

  val scalaVersionSetting = scalaVersion := "2.11.7"

  def pluginVersionDefaults = List(
    propVersion(sdkVersion, "sdk", "1.5.1"),
    propVersion(protifyVersion, "protify", "1.1.4"),
    propVersion(trypVersion, "tryp", "28"),
    propVersion(tryplugVersion, "tryplug", "5")
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

  def pspec(user: String, pkg: String, version: SettingKey[String]) =
    macro Pspec.create
}
