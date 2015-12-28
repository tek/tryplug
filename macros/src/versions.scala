package tryp

import java.io.{StringWriter, InputStreamReader, File}
import java.nio.charset.Charset
import java.net.URL

import scala.concurrent.Future

import argonaut._, Argonaut._

import semverfi._

import sbt._
import sbt.Keys._

trait Versions
{
  import scala.concurrent.ExecutionContext.Implicits.global

  def info(user: String, repo: String, pkg: String) = {
    val url = mkUrl(user, repo, pkg)
    Future {
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

  def update(spec: PluginSpec)
  (implicit log: Logger) = {
    log.info(s"checking version of ${spec.pkg} (${spec.current})")
    if (spec.invalid)
      log.warn(s"invalid repo path '${spec.pkg}'")
    else {
      info(spec.user, spec.repo, spec.pkg)
        .map(_.decodeOption[PackageInfo])
        .andThen {
          case util.Success(Some(PackageInfo(_, v, _)))
          if Version(v) > Version(spec.current) ⇒
            writeVersion(spec.label, v)
            log.warn(
              s"updating version for ${spec.pkg}: ${spec.current} ⇒ $v")
        }
        .onFailure {
          case e ⇒ log.error(s"failed to fetch version for ${spec.pkg}: $e")
        }
    }
  }

  def projectDir: Option[File]

  def versionDirs = projectDir.toList

  def handlePrefix = ""

  def writeVersion(handle: String, version: String)(implicit log: Logger) = {
    def write(dir: File) = {
      val content = s"""$handlePrefix$handle in Global := "$version""""
      val f = dir / s"$handle.sbt"
      IO.write(f, content)
    }
    versionDirs map(write)
  }

  def mkUrl(user: String, repo: String, pkg: String) = {
    new URL(s"https://api.bintray.com/packages/$user/$repo/$pkg")
  }

  implicit def packageInfoCodecJson: CodecJson[PackageInfo] =
    casecodec3(PackageInfo.apply, PackageInfo.unapply)("name",
      "latest_version", "versions")

  case class PackageInfo(name: String, version: String, versions: List[String])
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
