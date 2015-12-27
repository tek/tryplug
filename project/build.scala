import sbt._
import Keys._

object TryplugBuild
extends Build
{
  override lazy val settings = super.settings ++ List(
    organization := "tryp.sbt",
    licenses += ("MIT", url("http://opensource.org/licenses/MIT"))
  )

  lazy val common = List(
    scalaSource in Compile := baseDirectory.value / "src",
    sbtPlugin := true,
    scalacOptions ++= List(
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

  lazy val tryplug = project in file(".") settings(
    name := "tryplug",
    resolvers += Resolver.sonatypeRepo("snapshots"),
    libraryDependencies ++= List(
      "io.argonaut" % "argonaut-scalaz_2.11" % "6.2-SNAPSHOT" changing(),
      "me.lessis" %% "semverfi" % "+"
    ),
    addSbtPlugin("me.lessis" % "bintray-sbt" % "0.3.0")
  ) settings(common: _*) aggregate(macros) dependsOn(macros)

  lazy val macros = project in file("macros") settings(
    name := "tryplug-macros",
    libraryDependencies +=
      "org.scalamacros" % "quasiquotes" % "2.+" cross CrossVersion.binary,
    addCompilerPlugin(
      "org.scalamacros" % "paradise" % "2.+" cross CrossVersion.full)
    ) settings(common: _*)
}
