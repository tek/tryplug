package tryp

import java.io.{StringWriter, InputStreamReader, File}
import java.nio.charset.Charset
import java.net.URL

import scala.concurrent.Future

import argonaut._, Argonaut._

import scalaz._, Scalaz._, concurrent.Task

import semverfi._

import sbt._
import sbt.Keys._

trait VersionApi
{
  def latest: Task[String]

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
  def latest = {
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

  def latest = {
    val response = request(mkUrl(spec.user, spec.repo, spec.pkg))
    response
      .map(_.decodeEither[PackageInfo])
      .map {
          case \/-(PackageInfo(_, v, _)) ⇒ v
          case -\/(t) ⇒
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
  def latest = {
    request(mkUrl(spec.org, spec.pkg))
      .map(parseResponse)
      .map {
          case \/-(v) ⇒ v
          case -\/(err) ⇒
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
      .flatMap { a ⇒ lens.get(a).toRightDisjunction(s"invalid json: $a") }
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
    }
  )

  val updatePluginVersionsTask = Def.task {
    implicit val log = streams.value.log
    val updater = versionUpdater.value
    versions.value foreach(updater.update)
  }
}
