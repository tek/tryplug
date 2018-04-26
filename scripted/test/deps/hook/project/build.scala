package tryp

import sbt._
import sbt.Keys._

import Types._

object B
extends AutoPlugin
with Tryplug

object Local
extends AutoPlugin
{
  object autoImport
  {
    val ensimeVersion = settingKey[String]("ensime version")
    val releaseVersion = settingKey[String]("release version")
  }
}
import Local.autoImport._

object PluginDeps
extends Libs
{
  val ensime = plugin("org.ensime", "sbt-ensime", ensimeVersion, MavenSource)
  val release = plugin("com.github.gseitz", "sbt-release", releaseVersion, BintraySource("sbt", "sbt-plugin-releases"))
  val corePlugins = List(release, ensime)
  val rootPlugins = List(ensime)
}
