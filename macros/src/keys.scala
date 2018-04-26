package tryp

import sbt._
import sbt.Keys._

object TrypKeys
{
  val Tryp = config("tryp")
  val trypVersion = settingKey[String]("tryp-build version") in Tryp
  val tryplugVersion = settingKey[String]("tryplug version") in Tryp
  val deps = settingKey[Libs]("plugin dependencies") in Tryp
}

object TrypKeysPlugin
extends AutoPlugin
{
  object autoImport
  {
    val Tryp = TrypKeys.Tryp
    val trypVersion = TrypKeys.trypVersion
    val tryplugVersion = TrypKeys.tryplugVersion
    val deps = TrypKeys.deps
  }
}

import TrypKeys.Tryp

object VersionUpdateKeys
{
  val versions = settingKey[Seq[TrypPluginDep]]("auto-updated plugins") in Tryp
  val projectDir = settingKey[File]("project base dirs into which to write versions") in Tryp
  val updateVersions = taskKey[Unit]("update plugin dep versions") in Tryp
  val autoUpdateVersions = settingKey[Boolean]("update plugin versions when updating dependencies") in Tryp
  val updateAllPlugins = settingKey[Boolean]("update all versions by default") in Tryp
  val updatePluginsInclude = settingKey[List[String]]("which plugins' versions to update") in Tryp
  val updatePluginsExclude = settingKey[List[String]]("which plugins' versions not to update") in Tryp
  val versionDirMap = settingKey[Map[String, List[File]]]("settingKey -> directories for version updates") in Tryp
  val handlePrefixMap = settingKey[Map[File, String]]("dir -> prefix for version updates") in Tryp
}
