package tryp

import sbt._
import sbt.Keys._

import bintray._
import BintrayKeys._

import Types._

trait Tryplug
{
  import TrypKeys._

  def plugin(org: String, name: String, version: SettingKey[String]) =
    PluginTrypId.pluginDep(org, name, version)

  def compilerSettings = List(
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

  def commonBasicSettings: List[Setting[_]] = List(
    updateOptions := updateOptions.value.withCachedResolution(true)
  ) ++ compilerSettings

  def trypSettings = commonBasicSettings ++ List(
    organization := "tryp",
    scalaVersionSetting
  )

  def trypPluginSettings = commonBasicSettings ++ List(
    organization := "tryp.sbt",
    sbtPlugin := true,
    scalaSource in Compile := baseDirectory.value / "src",
    licenses += ("MIT", url("http://opensource.org/licenses/MIT")),
    bintrayRepository in bintray := "sbt-plugins",
    bintrayOrganization in bintray := None,
    publishMavenStyle := false,
    publishArtifact in (Compile, packageDoc) := false,
    publishArtifact in (Compile, packageSrc) := false
  )

  def pluginSubProject(name: String) = {
    Project(name, file(name))
      .settings(trypPluginSettings: _*)
      .settings(deps(name): _*)
      .settings(deps.pluginVersions(name): _*)
      .settings(
        bintrayTekResolver,
        VersionUpdateKeys.autoUpdateVersions := true
      )
      .dependsOn(deps.refs(name): _*)
  }

  def deps: Deps = NoDeps

  def pluginRoot(name: String) = {
    pluginSubProject(name).in(file("."))
      .settings(
        publish := (),
        publishLocal := ()
      )
  }

  def pluginProject(name: String) = {
    import VersionUpdateKeys._
    pluginRoot(name)
      .settings(
        updateAllPlugins := true,
        versionDirMap ++= {
          val d = projectDir.value
          val dirs = List(d, d / "project")
          Map("trypVersion" → dirs, "tryplugVersion" → dirs)
        },
        handlePrefixMap += ((projectDir.value / "project") → "P.")
      )
  }

  def projectBuildName = "project-build"

  def projectBuild = {
    pluginProject(projectBuildName)
  }

  val scalaVersionSetting = scalaVersion := "2.11.8"

  def pluginVersionDefaults = List(
    propVersion(sdkVersion, "sdk", "1.5.1"),
    propVersion(protifyVersion, "protify", "1.1.4"),
    propVersion(trypVersion, "tryp", "28"),
    propVersion(tryplugVersion, "tryplug", "40"),
    propVersion(coursierVersion, "coursier", "1.0.0-M12")
  )

  val homeDir = sys.env.get("HOME").map(d ⇒ new File(d))

  def bintrayPluginResolver(name: String) = {
    val u = url(s"https://dl.bintray.com/$name/sbt-plugins")
    val n = s"bintray-$name-sbt"
    resolvers += Resolver.url(n, u)(Resolver.ivyStylePatterns)
  }

  lazy val bintrayTekResolver = bintrayPluginResolver("tek")

  def propVersion(setting: SettingKey[String], name: String, alt: String) = {
    setting <<= setting or Def.setting(sys.props.getOrElse(name, alt))
  }

  def bintraySpec(user: String, repo: String, org: String, pkg: String,
    version: SettingKey[String]) =
      macro Pspec.bintray

  def nexusUri(host: String) = s"https://$host/nexus/content/repositories"

  def nexusPattern = "[organisation]/[module]/[revision]/" +
    "[artifact]-[revision](-[timestamp]).[ext]"

  def projectUpdater(user: String, repo: String, org: String, id: String,
    version: SettingKey[String], prefix: String = "") = Def.task {
      implicit val log = streams.value.log
      val updater = new Versions {
        override def projectDir = Some(baseDirectory.value / "project")
        override def defaultHandlePrefix = prefix
      }
      updater.update(
        bintraySpec(user, repo, org, id, version))
  }
}
