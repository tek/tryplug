package tryp

import sbt._
import Keys._

object GlobalKeysPlug
extends AutoPlugin
{
  override def requires = plugins.JvmPlugin
  override def trigger = allRequirements

  val autoImport = TrypKeys
}

object UserLevel
extends AutoPlugin
with Tryplug
{
  override def requires = PluginVersionUpdate

  override def projectSettings = super.projectSettings ++ Seq(
    name := userLevelName,
    VersionUpdateKeys.autoUpdateVersions := true
  ) ++ basicPluginSettings ++ deps(userLevelName) ++
    deps.pluginVersions(userLevelName)
}
