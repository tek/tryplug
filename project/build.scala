import sbt._
import Keys._

object TryplugBuild
extends Build
{
  override lazy val settings = super.settings ++ Seq(
    organization := "tryp.sbt",
    licenses += ("MIT", url("http://opensource.org/licenses/MIT"))
  )

  lazy val common = Seq(
    scalaSource in Compile := baseDirectory.value / "src",
    organization := "tryp.sbt",
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
    )
  )

  lazy val root = project in file(".") settings(
    name := "tryplug",
    libraryDependencies += "io.argonaut" %% "argonaut" % "+",
    addSbtPlugin("me.lessis" % "bintray-sbt" % "0.3.0")
  )
}
