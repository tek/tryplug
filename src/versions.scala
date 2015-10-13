package tryp

import java.io.{StringWriter, InputStreamReader, File}
import java.nio.charset.Charset
import java.net.URL

import scala.concurrent.Future

import argonaut._, Argonaut._

import semverfi._

import sbt._

object Versions
{
  import scala.concurrent.ExecutionContext.Implicits.global

  def info(grp: String, pkg: String) = {
    val url = mkUrl(grp, pkg)
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

  def update(grp: String, pkg: String, handle: String, current: String)
  (implicit log: Logger) = {
    log.info(s"checking version of $handle ($current)")
    info(grp, pkg)
      .map(_.decodeOption[PackageInfo])
      .andThen {
        case util.Success(Some(PackageInfo(_, v, _)))
        if Version(v) > Version(current) ⇒
          writeVersion(handle, v)
          log.warn(s"updating version for $handle: $current ⇒ $v")
      }
      .onFailure {
        case e ⇒ log.error(s"failed to fetch version for $pkg: $e")
      }
  }

  val versionDirs =
    sys.env.get("HOME")
      .map { home ⇒
        val plug = new File(home) / ".sbt" / "0.13" / "plugins"
        Seq(plug, plug / "project")
      }
      .getOrElse(Nil)

  def writeVersion(handle: String, version: String)(implicit log: Logger) = {
    def write(dir: File) = {
      val v = s"${handle}Version"
      val content = s"""$v in Global := "$version""""
      val f = dir / s"$v.sbt"
      IO.write(f, content)
    }
    versionDirs map(write)
  }

  def mkUrl(grp: String, pkg: String) = {
    new URL(s"https://api.bintray.com/packages/$grp/sbt-plugins/$pkg")
  }

  implicit def packageInfoCodecJson: CodecJson[PackageInfo] =
    casecodec3(PackageInfo.apply, PackageInfo.unapply)("name",
      "latest_version", "versions")

  case class PackageInfo(name: String, version: String, versions: List[String])
}
