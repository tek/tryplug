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
