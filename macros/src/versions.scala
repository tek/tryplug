package tryp

import java.io.{StringWriter, InputStreamReader, File}
import java.nio.charset.Charset
import java.net.{URL, URLEncoder}

import scala.concurrent.Future
import scala.util.Failure
import scala.concurrent.ExecutionContext.Implicits.global

import io.circe._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._

import cats.syntax.cartesian._
import cats.syntax.either._
import cats.syntax.foldable._
import cats.syntax.traverse._
import cats.instances.option._
import cats.instances.future._
import cats.instances.list._
import cats.data.StateT
import cats.kernel.Order

import sbt._
import sbt.Keys._
import sbt.io.Using

import VersionStateT._

object VersionStateT
{
  type VState[A] = StateT[Future, VersionsMeta, A]
}

object sequenceSettings
{
  def apply[A](settings: Seq[Def.Initialize[A]]): Def.Initialize[List[A]] =
    settings.reverse.foldRight(Def.setting(List.empty[A]))((z, a) => z.zipWith(a)(_ :: _))
}

object Http
{
  def request(url: URL): Future[String] = {
    Future {
      url.openConnection()
      Using.urlReader(IO.utf8)(url) { in =>
        val sw = new StringWriter
        val buf = Array.ofDim[Char](8192)
        Stream.continually(in.read(buf, 0, 8192))
          .takeWhile(_ != -1)
          .foreach(sw.write(buf, 0, _))
        sw.toString
      }
    }
  }

  def readResponse[A: Decoder]
  (response: String)
  (decodeResponse: A => Either[String, SemanticVersion])
  : Either[String, SemanticVersion] =
    for {
      payload <- decode[A](response).leftMap(_.toString)
      version <- decodeResponse(payload)
    } yield version

  def parseError(api: String, pkg: String, error: String): VState[SemanticVersion] =
    for {
      _ <- Log.warn(s"invalid $api version info for $pkg: $error")
    } yield Versions.zero

  def fetch[A: Decoder](plugin: PluginVersion, url: URL, api: String)
  (decodeResponse: A => Either[String, SemanticVersion])
  : VState[SemanticVersion] =
    for {
      response <- StateT.liftF(request(url))
      version <- readResponse(response)(decodeResponse) match {
        case Right(a) => StateT.pure[Future, VersionsMeta, SemanticVersion](a)
        case Left(error) => parseError(api, plugin.pkg, error)
      }
    } yield version
}

object Bintray
{
  case class PackageInfo(name: String, latest_version: String, versions: List[String])

  def decodeResponse(payload: PackageInfo): Either[String, SemanticVersion] =
    payload match { case PackageInfo(_, v, _) => SemanticVersion.parse(v) }

  def mkUrl(user: String, repo: String, pkg: String) =
    new URL(s"https://api.bintray.com/packages/$user/$repo/$pkg")

  def fetch(plugin: PluginVersion, user: String, repo: String): VState[SemanticVersion] =
    Http.fetch(plugin, mkUrl(user, repo, plugin.pkg), "bintray")(decodeResponse)
}

object Maven
{
  case class Pkg(latestVersion: String)
  case class Response(docs: List[Pkg])
  case class Payload(response: Response)

  def decodeResponse(payload: Payload): Either[String, SemanticVersion] =
    payload match {
      case Payload(Response(Pkg(v) :: _)) => SemanticVersion.parse(v)
      case _ => Left("no versions in maven response")
    }

  def fetch(plugin: PluginVersion): VState[SemanticVersion] =
    Http.fetch(plugin, mkUrl(plugin.org, plugin.pkg), "maven")(decodeResponse)

  def mkUrl(org: String, pkg: String) = {
    val path = "https://search.maven.org/solrsearch/select"
    val query = URLEncoder.encode(s"""g:"$org" AND a:"$pkg"""", "UTF-8")
    new URL(s"$path?q=$query")
  }
}

case class VersionsMeta(
  log: Logger,
  projectDir: Option[File],
  versionDirs: Map[String, List[File]],
  handlePrefixes: Map[File, String],
  scalaVersion: String,
  sbtVersion: String
)

case class PluginVersion(source: PluginSource, org: String, pkg: String, label: String, current: SemanticVersion)

object PluginVersion
{
  def cons(dep: TrypPluginDep) = Def.settingDyn {
    val current = SemanticVersion.parse(dep.version.value).getOrElse(Versions.zero)
    Def.setting(PluginVersion(dep.source.source, dep.source.org, dep.source.pkg, dep.version.key.label, current))
  }
}

object Log
{
  def debug(msg: String): VState[Unit] =
    StateT.inspectF(a => Future(a.log.debug(msg)))

  def warn(msg: String): VState[Unit] =
    StateT.inspectF(a => Future(a.log.warn(msg)))
}

object WriteVersion
{
  def defaultHandlePrefix = ""

  def versionDirs(handle: String): VState[List[File]] =
    for {
      versionDirs <- StateT.inspect[Future, VersionsMeta, Map[String, List[File]]](_.versionDirs)
      projectDir <- StateT.inspect[Future, VersionsMeta, Option[File]](_.projectDir)
    } yield versionDirs.get(handle).getOrElse(projectDir.toList)

  def handlePrefix(dir: File, handle: String): VState[String] =
    for {
      handlePrefixes <- StateT.inspect[Future, VersionsMeta, Map[File, String]](_.handlePrefixes)
    } yield handlePrefixes.get(dir).getOrElse(defaultHandlePrefix)

  def writeFile(handle: String, version: String)(dir: File): VState[Unit] = {
    for {
      prefix <- handlePrefix(dir, handle)
      _ <- StateT.liftF {
        val content = s"""$prefix$handle in Global := "$version""""
        val f = dir / s"$handle.sbt"
        Future(IO.write(f, content))
      }
    } yield ()
  }

  def write(handle: String, version: String) =
    for {
      dirs <- versionDirs(handle)
      _ <- dirs.traverse(writeFile(handle, version))
    } yield ()

  def apply(version: SemanticVersion)(plugin: PluginVersion): VState[Unit] =
    for {
      _ <- Log.warn(s"updating version for ${plugin.pkg}: ${plugin.current} => $version")
      _ <- write(plugin.label, version.toString)
    } yield ()
}

object Versions
{
  val localRepo: Future[File] =
    for {
      homeO <- Future(sys.env.get("HOME"))
      home <- homeO.map(Future.successful).getOrElse(Future.failed(new Throwable("couldn't access $HOME")))
    } yield new File(home) / ".ivy2" / "local"

  def localVersion(plugin: PluginVersion): VState[SemanticVersion] =
    for {
      repo <- StateT.liftF(localRepo)
      scalaVersion <- StateT.inspect[Future, VersionsMeta, String](_.scalaVersion)
      sbtVersion <- StateT.inspect[Future, VersionsMeta, String](_.sbtVersion)
    } yield {
      val glob = repo / plugin.org / plugin.pkg / s"scala_$scalaVersion" / s"sbt_$sbtVersion" * "*"
      val dirs: List[String] = "0" :: glob.get.map(_.getName.toString).toList
      dirs.flatMap(a => SemanticVersion.parse(a).toOption).maximumOption.getOrElse(SemanticVersion(0))
    }

  def fetchVersion(plugin: PluginVersion): VState[SemanticVersion] = {
    plugin.source match {
      case MavenSource => Maven.fetch(plugin)
      case BintraySource(user, repo) => Bintray.fetch(plugin, user, repo)
      case _ => StateT.pure(zero)
    }
  }

  def upToDate(plugin: PluginVersion)
  : VState[Unit] =
    for {
      _ <- Log.debug(s"version for ${plugin.pkg} is up to date")
    } yield ()

  val zero = SemanticVersion(0)

  def versionUpdateInfo(plugin: PluginVersion): VState[SemanticVersion] = {
    for {
      _ <- Log.debug(s"checking version of ${plugin.pkg} (${plugin.current})")
      remote <- fetchVersion(plugin)
      local <- localVersion(plugin)
      latest = if (Order.gt(local, remote)) local else remote
    } yield latest
  }

  def newVersion(plugin: PluginVersion): VState[Option[SemanticVersion]] =
    for {
      latest <- versionUpdateInfo(plugin)
    } yield if(Order.gt(latest, plugin.current)) Some(latest) else None

  def update(meta: VersionsMeta, plugin: PluginVersion): Unit = {
    val fa = for {
      version <- newVersion(plugin)
      handler = version.map(a => WriteVersion(a) _).getOrElse(upToDate _)
      _ <- handler(plugin)
    } yield ()
    fa.runA(meta).onComplete {
      case Failure(t) =>
        meta.log.error(s"failed to fetch version for ${plugin.pkg}: $t")
      case _ =>
    }
  }

//   def versionDirs(handle: String) =
//     versionDirMap.get(handle) getOrElse projectDir.toList

//   def defaultHandlePrefix = ""

//   def handlePrefix(dir: File, handle: String) = {
//     handlePrefixMap.get(dir) getOrElse defaultHandlePrefix
//   }

//   def writeVersion(handle: String, version: String) =
//   {
//     def write(dir: File) = {
//       val prefix = handlePrefix(dir, handle)
//       val content = s"""$prefix$handle in Global := "$version""""
//       val f = dir / s"$handle.sbt"
//       IO.write(f, content)
//     }
//     versionDirs(handle) map(write)
//   }
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
    updateVersions := updatePluginVersionsTask.value,
    versionDirMap := Map(),
    handlePrefixMap := Map(),
    update := (update dependsOn Def.taskDyn {
      if (autoUpdateVersions.value) updatePluginVersionsTask
      else Def.task(())
    }).value,
    updateAllPlugins := false,
    updatePluginsInclude := List(),
    updatePluginsExclude := List()
  )

  val updatePluginVersionsTask = Def.taskDyn {
    val meta = VersionsMeta(
      streams.value.log,
      Option(autoImport.projectDir.value),
      versionDirMap.value,
      handlePrefixMap.value,
      scalaBinaryVersion.value,
      sbtVersion.value
    )
    val vs = versions.value
    val include = updatePluginsInclude.value
    val exclude = updatePluginsExclude.value
    val wanted =
      if (updateAllPlugins.value) vs
      else vs.filter(a => include.contains(a.source.pkg))
    val pluginVersions: Seq[Def.Initialize[PluginVersion]] =
      wanted
        .filterNot(a => exclude.contains(a.source.pkg))
        .map(a => PluginVersion.cons(a))
    if (!sys.env.get("TRYP_SBT_DISABLE_VERSION_UPDATE").isDefined)
      Def.task(sequenceSettings(pluginVersions).value.foreach(a => Versions.update(meta, a)))
    else Def.task(())
  }
}
