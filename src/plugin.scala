package tryp

import sbt._
import Keys._

object TrypGen
extends AutoPlugin
{
  override def trigger = allRequirements
  override def projectSettings = super.projectSettings ++ Seq(
    commands ++= List(genTryp, genTrypAndroid)
    )

  def ptryp(plug: String, buildHelper: String) = s"""enablePlugins($plug)
lazy val `project` = $buildHelper"""

  val trypver = """trypVersion := "109.0.0""""

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
    Command.command(s"gen-$cmd") { state =>
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
