import ReleaseTransformations._
import sbtrelease.Version.Bump

val common = List(
  organization := "tryp.sbt",
  licenses += ("MIT", url("http://opensource.org/licenses/MIT")),
  scalaSource in Compile := baseDirectory.value / "src",
  scalaSource in Test := baseDirectory.value / "test-src",
  sbtPlugin := true,
  publishMavenStyle := false,
  publishArtifact in (Compile, packageDoc) := false,
  publishArtifact in (Compile, packageSrc) := false,
  bintrayOrganization in bintray := None,
  resolvers ++= List(
    Resolver.sonatypeRepo("snapshots"),
    Resolver.sonatypeRepo("releases")
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
  ),
  releaseProcess := Seq[ReleaseStep](
    inquireVersions,
    setReleaseVersion,
    commitReleaseVersion,
    publishArtifacts,
    tagRelease,
    setNextVersion,
    commitNextVersion
  ),
  releaseIgnoreUntrackedFiles := true,
  releaseVersionBump := Bump.Major
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
      "io.circe" %% "circe-core" % "0.8.0",
      "io.circe" %% "circe-parser" % "0.8.0",
      "io.circe" %% "circe-generic" % "0.8.0",
      "me.lessis" %% "semverfi" % "0.+",
      "org.scalamacros" % "quasiquotes" % "2.+" cross CrossVersion.binary,
      "org.specs2" %% "specs2-core" % "3.8.9" % "test",
      "org.specs2" %% "specs2-matcher-extra" % "3.8.9" % "test"
    ),
    addCompilerPlugin("org.scalamacros" % "paradise" % "2.+" cross CrossVersion.patch)
    )

lazy val scripted = (project in file("scripted"))
  .settings(scriptedSettings: _*)
  .settings(
    resolvers += Resolver.typesafeIvyRepo("releases"),
    sbtTestDirectory := baseDirectory.value / "test",
    scriptedRun := scriptedRun.dependsOn(publishLocal in macros, publishLocal in tryplug).value,
    scriptedBufferLog := false,
    scriptedLaunchOpts ++= Seq(
      "-Xmx2048m",
      "-XX:MaxPermSize=1024m",
      s"-Dtryp.projectsdir=${baseDirectory.value / "meta"}",
      s"-Dtryplug.version=${version.value}"
    )
  )
