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
