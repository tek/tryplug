import ReleaseTransformations._
import sbtrelease.Version.Bump

val common = List(
  organization := "io.tryp",
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
    "-language:higherKinds",
    "-Ypartial-unification"
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

val circeVersion = "0.9.3"

lazy val tryplug = (project in file("."))
  .settings(common: _*)
  .settings(
    name := "tryplug",
    addSbtPlugin("org.foundweekends" %% "sbt-bintray" % "0.5.1")
  )
  .aggregate(macros)
  .dependsOn(macros)

lazy val macros = (project in file("macros"))
  .settings(common: _*)
  .settings(
    name := "tryplug-macros",
    libraryDependencies ++= List(
      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "com.lihaoyi" %% "fastparse" % "0.4.4",
      "org.specs2" %% "specs2-core" % "3.8.9" % "test",
      "org.specs2" %% "specs2-matcher-extra" % "3.8.9" % "test",
    ),
    addCompilerPlugin("org.scalamacros" % "paradise" % "2.+" cross CrossVersion.patch)
  )

lazy val scripted = (project in file("scripted"))
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
