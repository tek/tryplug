package tryp

import reflect.macros.Context

import sbt._
import sbt.Keys._

import Types._

class TrypId(val id: ModuleID, depspec: DepSpec, path: String,
  sub: List[String], dev: Boolean)
{
  def no = new TrypId(id, depspec, path, sub, false)

  def development = Env.development && dev

  def dep = if(development) TrypId.empty else depspec

  def projects = {
    if (sub.isEmpty) List(RootProject(Env.localProject(path)))
    else sub map { n ⇒ ProjectRef(Env.localProject(path), n) }
  }

  def refs = {
    if (development) projects.map(a ⇒ a: SbtDep)
    else List()
  }

  override def toString = s"TrypId($id, $path, $sub, $dev)"

  def info = {
    if (dev) s"$path${sub mkString("[", ",", "]")}"
    else id.toString
  }
}

case class PluginSpec(user: String, repo: String, pkg: String, label: String,
  current: String)
{
  def invalid = user == Pspec.invalid
}

class PluginTrypId(depspec: DepSpec, path: String, sub: List[String],
  dev: Boolean, val version: Setting[Seq[PluginSpec]])
extends TrypId(TrypId.invalid, depspec, path, sub, dev)
{
  def aRefs = super.projects

  override def development = super.development && Env.trypDebug.isDefined
}

object TrypId
{
  def empty = libraryDependencies ++= List()

  def plain(depspec: DepSpec) = new TrypId(invalid, depspec, "", List(), false)

  def invalid = "invalid" % "invaild" % "1"
}

object Deps
{
  def ddImpl(c: Context)(id: c.Expr[ModuleID], path: c.Expr[String],
    sub: c.Expr[String]*) =
  {
    import c.universe._
    c.Expr[TrypId] {
      q"""new tryp.TrypId(
        $id, libraryDependencies += $id, $path, List(..$sub), true
      )
      """
    }
  }

  def dImpl(c: Context)(id: c.Expr[ModuleID]) = {
    import c.universe._
    c.Expr[TrypId] { q"tryp.TrypId.plain(libraryDependencies += $id)" }
  }

  def pdImpl(c: Context)(org: c.Expr[String], pkg: c.Expr[String],
    version: c.Expr[SettingKey[String]], user: c.Expr[String],
    repo: c.Expr[String], github: c.Expr[String], sub: c.Expr[String]*
    ) = {
    import c.universe._
    c.Expr[PluginTrypId] {
      val vspec = Pspec.create(c)(user, repo, pkg, version)
      q"""new tryp.PluginTrypId(
        plugin($org, $pkg, $version), $github, List(..$sub), true,
          VersionUpdateKeys.versions += $vspec
      )
      """
    }
  }
}

class ModuleIDOps(id: ModuleID)
{
  def isAar = {
    id.explicitArtifacts.exists(_.`type` == "aar")
  }
}

trait ToModuleIDOps
{
  implicit def ToModuleIDOps(id: ModuleID) = new ModuleIDOps(id)
}

object ModuleID
extends ToModuleIDOps

trait Deps
{
  implicit def moduleIDtoTrypId(id: ModuleID) =
    new TrypId(id, libraryDependencies += id, "", List(), false)

  implicit class MapOps[A, B](m: Map[A, _ <: Seq[B]]) {
    def fetch(key: A) = m.get(key).toList.flatten
  }

  def ids(i: TrypId*) = List[TrypId](i: _*)

  def deps: Map[String, List[TrypId]] = Map(
    "unit" → unit
  )

  def dd(id: ModuleID, path: String, sub: String*) = macro Deps.ddImpl

  def d(id: ModuleID) = macro Deps.dImpl

  def pd(org: String, pkg: String, version: SettingKey[String],
    user: String, repo: String, github: String, sub: String*) =
      macro Deps.pdImpl

  def manualDd(normal: DepSpec, path: String, sub: String*) =
    new TrypId(TrypId.invalid, normal, path, sub.toList, true)

  def defaultResolvers = List(
      Resolver.sonatypeRepo("releases"),
      Resolver.sonatypeRepo("snapshots"),
      Resolver.sonatypeRepo("staging"),
      Resolver.bintrayRepo("scalaz", "releases"),
      Resolver.jcenterRepo
    )

  def resolvers: Map[String, List[Resolver]] = Map()

  // `libraryDependencies += id` for each dep
  // if env is development, devdeps return empty settings
  def apply(name: String): Setts = {
    List(Keys.resolvers ++= defaultResolvers ++ this.resolvers.fetch(name)) ++
      (common ++ deps.fetch(name)).map(_.dep)
  }

  // ProjectRef instances for each devdep's subprojects
  // if env isn't development, nothing is returned
  def refs(name: String) = {
    (common ++ deps.fetch(name)) flatMap(_.refs)
  }

  // PluginSpec instance for each dep
  def pluginVersions(name: String) = {
    deps fetch(name) collect {
      case i: PluginTrypId ⇒ i.version
    }
  }

  val scalazV = "7.2.+"

  val specsV = "+"

  val scalatestV = "2.2.+"

  def common = ids(
    "org.scalaz" %% "scalaz-concurrent" % scalazV,
    "org.scalaz.stream" %% "scalaz-stream" % "+"
  )

  def commonTestIds = ids(
    "org.scalatest" %% "scalatest" % scalatestV,
    "org.specs2" %% "specs2-core" % specsV
  )

  def commonTestIdsScoped = ids(
    "org.scalatest" %% "scalatest" % scalatestV % "test",
    "org.specs2" %% "specs2-core" % specsV % "test"
  )

  def unit = commonTestIds
}

object NoDeps
extends Deps

object Pspec
{
  val invalid = "[-invalid-]"

  def create(c: Context)(user: c.Expr[String], repo: c.Expr[String],
    pkg: c.Expr[String], version: c.Expr[SettingKey[String]]): c.Expr[Any] =
  {
    import c.universe._
    c.Expr(
      q"""
      PluginSpec($user, $repo, $pkg, $version.key.label, $version.value)
      """
    )
  }
}
