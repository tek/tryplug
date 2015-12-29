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

  object TrypDeps
  extends PluginDeps
  {
    override def deps = super.deps ++ Map(
      userLevelName → userLevel
    )

    val huy = "com.hanhuy.sbt"
    val sdkName = "android-sdk-plugin"
    val protifyName = "protify"

    val userLevel = ids(
      plugin(huy, sdkName, sdkVersion, s"pfn/$sdkName")
        .bintray("pfn"),
      plugin(huy, s"android-$protifyName", protifyVersion, s"pfn/$protifyName")
        .bintray("pfn"),
      plugin(trypOrg, s"tryp-$androidName", trypVersion, "tek/sbt-tryp",
        List(androidName)).bintray("tek"),
      plugin(trypOrg, "tryplug", tryplugVersion, "tek/tryplug",
        List("tryplug", "macros")).bintray("tek")
    )
  }

  override def deps = TrypDeps

  def userLevelDebugDeps = {
    Project(userLevelName, file("."))
      .settings(pluginVersionDefaults: _*)
      .dependsOn(deps.refs(userLevelName): _*)
  }
}
