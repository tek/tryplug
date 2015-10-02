package tryp

import sbt._
import Keys._

object GlobalKeysPlug
extends AutoPlugin
{
  override def requires = plugins.JvmPlugin
  override def trigger = allRequirements

  val autoImport = TrypKeys

  import TrypKeys._

  override def globalSettings = super.globalSettings ++
    Seq(
      trypVersion := "1",
      sdkVersion := "1",
      protifyVersion := "1"
    )
}
