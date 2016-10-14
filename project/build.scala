import sbt._
import Keys._

import ScriptedPlugin._

object TryplugBuild
extends Build
{
  override lazy val settings = super.settings ++ List(
    organization := "tryp.sbt",
    licenses += ("MIT", url("http://opensource.org/licenses/MIT"))
  )

  lazy val common = List(
    scalaSource in Compile := baseDirectory.value / "src",
    scalaSource in Test := baseDirectory.value / "test-src",
    sbtPlugin := true,
    publishMavenStyle := false,
    publishArtifact in (Compile, packageDoc) := false,
    publishArtifact in (Compile, packageSrc) := false,
    resolvers ++= List(
      Resolver.sonatypeRepo("snapshots"),
      Resolver.sonatypeRepo("releases"),
      Resolver.bintrayRepo("scalaz", "releases")
    ),
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
      addSbtPlugin("me.lessis" % "bintray-sbt" % "0.3.0")
    )
    .aggregate(macros)
    .dependsOn(macros)

  lazy val macros = (project in file("macros"))
    .settings(common: _*)
    .settings(
      name := "tryplug-macros",
      libraryDependencies ++= List(
        "io.circe" %% "circe-core" % "0.+",
        "io.circe" %% "circe-parser" % "0.+",
        "io.circe" %% "circe-generic" % "0.+",
        "me.lessis" %% "semverfi" % "0.+",
        "org.scalamacros" % "quasiquotes" % "2.+" cross CrossVersion.binary,
        "org.scalaz" %% "scalaz-concurrent" % "7.1.+",
        "org.specs2" %% "specs2-core" % "3.8.+" % "test",
        "org.specs2" %% "specs2-matcher-extra" % "3.8.+" % "test"
      ),
      addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.0-M14"),
      addCompilerPlugin(
        "org.spire-math" % "kind-projector" % "0.9.0" cross CrossVersion.binary
      ),
      addCompilerPlugin(
        "org.scalamacros" % "paradise" % "2.+" cross CrossVersion.full)
      )

  lazy val scripted = (project in file("scripted"))
    .settings(scriptedSettings: _*)
    .settings(
      resolvers += Resolver.typesafeIvyRepo("releases"),
      sbtTestDirectory := baseDirectory.value / "test",
      scriptedRun <<=
        scriptedRun dependsOn(publishLocal in macros, publishLocal in tryplug),
      scriptedBufferLog := false,
      scriptedLaunchOpts ++= Seq(
        "-Xmx2048m",
        "-XX:MaxPermSize=1024m",
        s"-Dtryp.projectsdir=${baseDirectory.value / "meta"}",
        s"-Dtryplug.version=${version.value}"
      )
    )
}
