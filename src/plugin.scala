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
  }

  def userLevelName = "user-level"

  def updateTryplugVersion =
    projectUpdater("tek", "sbt-plugins", "tryp.sbt", "tryplug",
      TrypKeys.tryplugVersion, prefix = "tryp.TrypKeys")

  override def projectSettings = super.projectSettings ++ List(
    name := userLevelName,
    VersionUpdateKeys.autoUpdateVersions := false,
    bintrayTekResolver,
    publishTo := None,
    update <<= update dependsOn updateTryplugVersion,
    bintrayPluginResolver("pfn"),
    addSbtPlugin("com.hanhuy.sbt" % "key-path" % "0.2")
  ) ++ trypPluginSettings ++ deps(userLevelName) ++
    deps.pluginVersions(userLevelName)

  object TrypDeps
  extends PluginDeps
  {
    import Plugins._

    override def deps = super.deps ++ Map(
      userLevelName → userLevel
    )

    val userLevel = ids(tryplug)
  }

  override def deps = TrypDeps

  def userLevelDebugDeps = {
    Project(userLevelName, file("."))
      .settings(pluginVersionDefaults: _*)
      .dependsOn(deps.refs(userLevelName): _*)
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

  val trypver = """trypVersion := "85""""

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
    Command.command(s"gen-$cmd") { state ⇒
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
