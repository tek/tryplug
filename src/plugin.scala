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
    def tryplugVersion = TrypKeys.tryplugVersion
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
      TrypKeys.tryplugVersion)

  override def projectSettings = super.projectSettings ++ List(
    name := userLevelName,
    VersionUpdateKeys.autoUpdateVersions := true,
    VersionUpdateKeys.updateAllPlugins := true,
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

  val ptryp = "lazy val `project` = trypProjectBuild"

  val trypver = """trypVersion := "83""""

  val pptryp = """
resolvers += Resolver.url(
  "bintray-tek-sbt",
  url("https://dl.bintray.com/tek/sbt-plugins")
)(Resolver.ivyStylePatterns)
addSbtPlugin("tryp.sbt" % "tryp-build" % "83")
  """

  val pppbuild = """
import sbt._

object P
extends Plugin
{
  val trypVersion = settingKey[String]("tryp version")
}
  """

  def genTryp = Command.command("gen-tryp") { state ⇒
    val extracted = sbt.Project.extract(state)
    import extracted._
    val pro = get(baseDirectory) / "project"
    val pp = pro / "project"
    val ppp = pp / "project"
    IO.createDirectory(pp)
    IO.write(pro / "trypVersion.sbt", trypver)
    IO.write(pro / "tryp.sbt", ptryp)
    IO.write(pp / "trypVersion.sbt", trypver)
    IO.write(pp / "tryp.sbt", pptryp)
    IO.write(ppp / "build.scala", pppbuild)
    state
  }

  def genTrypAndroid = genTryp
}
