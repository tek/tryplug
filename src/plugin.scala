package tryp

import sbt._
import Keys._

object GlobalKeysPlug
extends AutoPlugin
{
  override def requires = plugins.JvmPlugin
  override def trigger = allRequirements

  object autoImport
  {
    def protifyVersion = TrypKeys.protifyVersion
    def sdkVersion = TrypKeys.sdkVersion
    def coursierVersion = TrypKeys.coursierVersion
  }
}

import TrypKeys._

object UserLevel
extends AutoPlugin
with Tryplug
{
  override def requires = PluginVersionUpdate

  object autoImport
  {
    def debugDeps = userLevelDebugDeps
    def useCoursier = TrypKeys.useCoursier
  }

  def userLevelName = "user-level"

  override def projectSettings = super.projectSettings ++ List(
    name := userLevelName,
    bintrayTekResolver,
    publishTo := None,
    bintrayPluginResolver("pfn"),
    useCoursier := false
  ) ++ trypPluginSettings ++ deps(userLevelName) ++
    deps.pluginVersions(userLevelName)

  def userLevelDebugDeps = {
    Project(userLevelName, file("."))
      .settings(pluginVersionDefaults: _*)
  }

  override object deps
  extends PluginDeps
  {
    override def deps = super.deps ++ Map(userLevelName -> ids(coursier))
  }
}

object TrypGen
extends AutoPlugin
{
  override def trigger = allRequirements
  override def projectSettings = super.projectSettings ++ Seq(
    commands ++= List(genTryp, genTrypAndroid)
    )

  def ptryp(plug: String, buildHelper: String) = s"""enablePlugins($plug)
lazy val `project` = $buildHelper"""

  val trypver = """trypVersion := "109.0.0""""

  def pptryp(dep: String) = s"""resolvers += Resolver.url(
  "bintray-tek-sbt",
  url("https://dl.bintray.com/tek/sbt-plugins")
)(Resolver.ivyStylePatterns)
libraryDependencies += Defaults.sbtPluginExtra(
  "tryp.sbt" % "tryp-$dep" % trypVersion.value,
  (sbtBinaryVersion in update).value,
  (scalaBinaryVersion in update).value
)
"""

  val pppbuild = """import sbt._

object P
extends Plugin
{
  val trypVersion = settingKey[String]("tryp version")
}"""

  def build(base: String, name: String, pkg: String = "tryp") =
    s"""package $pkg

import sbt._, Keys._

object Build
extends $base("$name")
{
  lazy val core = "core" !
}
"""

  def genCommand(cmd: String, dep: String, base: String, plug: String,
    buildHelper: String) =
    Command.command(s"gen-$cmd") { state =>
      val extracted = sbt.Project.extract(state)
      import extracted._
      val pro = get(baseDirectory) / "project"
      val pp = pro / "project"
      val ppp = pp / "project"
      IO.createDirectory(pp)
      IO.write(pro / "trypVersion.sbt", trypver)
      IO.write(pro / "tryp.sbt", ptryp(plug, buildHelper))
      IO.write(pp / "trypVersion.sbt", trypver)
      IO.write(pp / "tryp.sbt", pptryp(dep))
      IO.write(ppp / "build.scala", pppbuild)
      IO.write(pro / "build.scala", build(base, get(name)))
      state
    }

  def genTryp = genCommand("tryp", "build", "MultiBuild", "TrypBuildPlugin",
    "trypProjectBuild")

  def genTrypAndroid = genCommand("tryp-android", "android",
    "AndroidBuild", "TrypAndroidBuildPlugin", "trypAndroidProjectBuild")
}
