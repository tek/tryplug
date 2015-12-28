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

  object autoImport
  {
    def debugDeps = userLevelDebugDeps
  }

  def updateTryplugVersion = Def.task {
    implicit val log = streams.value.log
    val updater = new Versions {
      def projectDir = Some(baseDirectory.value / "project")
    }
    updater.update(
      bintraySpec("tek", "sbt-plugins", "tryplug", TrypKeys.tryplugVersion))
  }

  override def projectSettings = super.projectSettings ++ List(
    name := userLevelName,
    VersionUpdateKeys.autoUpdateVersions := true,
    bintrayTekResolver,
    publishTo := None,
    update <<= update dependsOn updateTryplugVersion,
    bintrayPluginResolver("pfn"),
    addSbtPlugin("com.hanhuy.sbt" % "key-path" % "0.2")
  ) ++ trypPluginSettings ++ deps(userLevelName) ++
    deps.pluginVersions(userLevelName)
}
