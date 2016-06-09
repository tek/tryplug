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
    publishMavenStyle := false,
    publishArtifact in (Compile, packageDoc) := false,
    publishArtifact in (Compile, packageSrc) := false,
    scalacOptions ++= List(
      "-feature",
      "-deprecation",
      "-unchecked",
      "-language:implicitConversions",
      "-language:reflectiveCalls",
      "-language:experimental.macros",
      "-language:existentials",
      "-language:higherKinds"
    )
  )

  lazy val tryplug = (project in file("."))
    .settings(common: _*)
    .settings(
      name := "tryplug",
      resolvers += Resolver.sonatypeRepo("snapshots"),
      addSbtPlugin("me.lessis" % "bintray-sbt" % "0.3.0")
    )
    .aggregate(macros)
    .dependsOn(macros)

  lazy val macros = (project in file("macros"))
    .settings(common: _*)
    .settings(
      name := "tryplug-macros",
      libraryDependencies ++= List(
        "io.circe" %% "circe-core" % "+",
        "io.circe" %% "circe-parser" % "+",
        "io.circe" %% "circe-generic" % "+",
        "me.lessis" %% "semverfi" % "+",
        "org.scalamacros" % "quasiquotes" % "2.+" cross CrossVersion.binary,
        "org.scalaz" %% "scalaz-concurrent" % "7.1.+"
      ),
      addCompilerPlugin(
        "org.scalamacros" % "paradise" % "2.+" cross CrossVersion.full)
      )
}
