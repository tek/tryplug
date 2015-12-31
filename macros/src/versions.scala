package tryp

import java.io.{StringWriter, InputStreamReader, File}
import java.nio.charset.Charset
import java.net.URL

import scala.concurrent.Future

import argonaut._, Argonaut._, ArgonautScalaz._

import scalaz._, Scalaz._, concurrent.Task

import semverfi._

import sbt._
import sbt.Keys._

trait VersionApi
{
  def spec: PluginSpec

  def latest = for {
    local ← latestLocal
    remote ← latestRemote
  } yield((Version(local) > Version(remote)) ? local | remote)

  val localRepo = sys.env.get("HOME").map(h ⇒ new File(h) / ".ivy2" / "local")

  def latestLocal = {
    Task {
      localRepo
        .map(_ / spec.org / spec.pkg / "scala_2.10" / "sbt_0.13" * "*")
        .map(_.get.map(_.getName.toString))
        .getOrElse(Nil)
        .:+("0")
        .maxBy(Version(_))
    }
  }

  def latestRemote: Task[String]

  def request(url: URL) = {
    Task {
      url.openConnection()
      Using.urlReader(IO.utf8)(url) { in ⇒
        val sw = new StringWriter
        val buf = Array.ofDim[Char](8192)
        Stream.continually(in.read(buf, 0, 8192))
          .takeWhile(_ != -1)
          .foreach(sw.write(buf, 0, _))
        sw.toString
      }
    }
  }
}

case class NopApi(spec: PluginSpec)(implicit log: Logger)
extends VersionApi
{
  def latestRemote = {
    Task("0")
  }
}

case class BintrayApi(spec: BintrayPluginSpec)(implicit log: Logger)
extends VersionApi
{
  case class PackageInfo(name: String, version: String, versions: List[String])

  implicit def packageInfoCodecJson: CodecJson[PackageInfo] =
    casecodec3(PackageInfo.apply, PackageInfo.unapply)("name",
      "latest_version", "versions")

  def latestRemote = {
    val response = request(mkUrl(spec.user, spec.repo, spec.pkg))
    response
      .map(_.decodeEither[PackageInfo])
      .map {
          case Right(PackageInfo(_, v, _)) ⇒ v
          case Left(t) ⇒
            log.error(s"invalid version info for ${spec.pkg}: $t")
            "0"
      }
  }

  def mkUrl(user: String, repo: String, pkg: String) = {
    new URL(s"https://api.bintray.com/packages/$user/$repo/$pkg")
  }
}

case class MavenApi(spec: MavenPluginSpec)(implicit log: Logger)
extends VersionApi
{
  def latestRemote = {
    request(mkUrl(spec.org, spec.pkg))
      .map(parseResponse)
      .map {
          case Right(v) ⇒ v
          case Left(err) ⇒
            log.error(s"invalid version info for ${spec.pkg}: $err")
            "0"
      }
  }

  lazy val lens = jObjectPL >=>
    jsonObjectPL("response") >=>
    jObjectPL >=>
    jsonObjectPL("docs") >=>
    jArrayPL >=>
    jsonArrayPL(0) >=>
    jObjectPL >=>
    jsonObjectPL("latestVersion") >=>
    jStringPL

  def parseResponse(response: String) = {
    argonaut.Parse.parse(response)
      .flatMap { a ⇒ lens.get(a).toRight(s"invalid json: $a") }
  }

  def mkUrl(org: String, pkg: String) = {
    val path = "https://search.maven.org/solrsearch/select"
    new URL(s"""$path?q=g:"$org"%20AND%20a:"$pkg"""")
  }
}

trait Versions
{
  import scala.concurrent.ExecutionContext.Implicits.global

  def update(spec: PluginSpec)(implicit log: Logger) = {
    log.info(s"checking version of ${spec.pkg} (${spec.current})")
    if (spec.invalid)
      log.warn(s"invalid repo path '${spec.pkg}'")
    else {
      api(spec).latest.runAsync {
        case -\/(t) ⇒
          log.error(s"failed to fetch version for ${spec.pkg}: $t")
        case \/-(v) if Version(v) > Version(spec.current) ⇒
          writeVersion(spec.label, v)
          log.warn(s"updating version for ${spec.pkg}: ${spec.current} ⇒ $v")
      }
    }
  }

  def api(spec: PluginSpec)(implicit log: Logger) = {
    spec match {
      case s: BintrayPluginSpec ⇒ BintrayApi(s)
      case s: MavenPluginSpec ⇒ MavenApi(s)
      case _ ⇒
        log.error(s"invalid plugin spec for version update: $spec")
        NopApi(spec)
    }
  }

  def projectDir: Option[File]

  def versionDirs = projectDir.toList

  def handlePrefix = ""

  def writeVersion(handle: String, version: String)(implicit log: Logger) =
  {
    def write(dir: File) = {
      val content = s"""$handlePrefix$handle in Global := "$version""""
      val f = dir / s"$handle.sbt"
      IO.write(f, content)
    }
    versionDirs map(write)
  }
}

object PluginVersionUpdate
extends AutoPlugin
{
  override def requires = plugins.JvmPlugin
  override def trigger = allRequirements

  val autoImport = VersionUpdateKeys
  import autoImport._

  override def projectSettings = super.projectSettings ++ List(
    versions := Nil,
    autoUpdateVersions := false,
    projectDir := (baseDirectory in ThisBuild).value,
    updateVersions <<= updatePluginVersionsTask,
    versionUpdater := {
      new Versions {
        def projectDir = Option(autoImport.projectDir.value)
      }
    },
    update <<= update dependsOn Def.taskDyn {
      if (autoUpdateVersions.value) updatePluginVersionsTask
      else Def.task()
    },
    updateAllPlugins := false,
    updatePluginsInclude := List(),
    updatePluginsExclude := List()
  )

  val updatePluginVersionsTask = Def.task {
    implicit val log = streams.value.log
    if (!sys.env.get("TRYP_SBT_DISABLE_VERSION_UPDATE").isDefined) {
      val updater = versionUpdater.value
      val wanted =
        if (updateAllPlugins.value) versions.value
        else versions.value.filter(
          a ⇒ updatePluginsInclude.value.contains(a.pkg))
      wanted
        .filterNot(a ⇒ updatePluginsExclude.value.contains(a.pkg))
        .foreach(updater.update)
    }
  }
}
