import sbt._
import Keys._

object TryplugBuild
extends Build
{
  override lazy val settings = super.settings ++ Seq(
    organization := "tryp",
    licenses += ("MIT", url("http://opensource.org/licenses/MIT"))
  )

  lazy val root = project in file(".") settings(
    name := "tryplug",
    organization := "tryp",
    scalaSource in Compile := baseDirectory.value / "src",
    sbtPlugin := true,
    scalacOptions ++= Seq(
      "-feature",
      "-deprecation",
      "-unchecked",
      "-language:implicitConversions",
      "-language:postfixOps",
      "-language:reflectiveCalls",
      "-language:experimental.macros",
      "-language:existentials",
      "-language:higherKinds"
    ),
    libraryDependencies += "io.argonaut" %% "argonaut" % "+"
  )
}
