package tryp

import reflect.macros.Context

import sbt._
import sbt.Keys._

import Types._

class TrypId(plainId: DepSpec, path: String, sub: Seq[String], dev: Boolean)
{
  def no = new TrypId(plainId, path, sub, false)

  def development = Env.development && dev

  def id = if(development) TrypId.empty else plainId

  def projects = {
    if (sub.isEmpty) List(RootProject(Env.localProject(path)))
    else sub map { n ⇒ ProjectRef(Env.localProject(path), n) }
  }

  def refs = {
    if (development) projects.map(a ⇒ a: ClasspathDep[ProjectReference])
    else List()
  }
}

class PluginTrypId(plainId: DepSpec, path: String, sub: Seq[String],
  dev: Boolean)
extends TrypId(plainId, path, sub, dev)
{
  def aRefs = super.projects

  override def development =
    super.development && sys.env.contains("TRYP_DEBUG")
}

object TrypId
{
  def empty = libraryDependencies ++= List()

  def plain(id: DepSpec) = new TrypId(id, "", Seq(), false)
}

object Deps
{
  def ddImpl(c: Context)(id: c.Expr[ModuleID], path: c.Expr[String],
    sub: c.Expr[String]*) =
  {
    import c.universe._
    c.Expr[TrypId] {
      q"""new tryp.TrypId(
        libraryDependencies += $id, $path, Seq(..$sub), true
      )
      """
    }
  }

  def dImpl(c: Context)(id: c.Expr[ModuleID]) = {
    import c.universe._
    c.Expr[TrypId] { q"tryp.TrypId.plain(libraryDependencies += $id)" }
  }

  def pdImpl(c: Context)(org: c.Expr[String], name: c.Expr[String],
    version: c.Expr[SettingKey[String]], path: c.Expr[String],
    sub: c.Expr[String]*) =
  {
    import c.universe._
    c.Expr[PluginTrypId] {
      q"""new tryp.PluginTrypId(
        plugin($org, $name, $version), $path, Seq(..$sub), true
      )
      """
    }
  }
}

trait DepsBase
{
  implicit def ModuleIDtoTrypId(id: ModuleID) =
    new TrypId(libraryDependencies += id, "", List(), false)

  implicit class MapShortcuts[A, B](m: Map[A, _ <: Seq[B]]) {
    def fetch(key: A) = m.get(key).toSeq.flatten
  }

  def ids(i: TrypId*) = Seq[TrypId](i: _*)

  def deps: Map[String, Seq[TrypId]] = Map(
    "unit" → unit,
    "integration" → integration
  )

  def dd(id: ModuleID, path: String, sub: String*) = macro Deps.ddImpl

  def d(id: ModuleID) = macro Deps.dImpl

  def pd(org: String, name: String, version: SettingKey[String], path: String,
    sub: String*) = macro Deps.pdImpl

  def manualDd(normal: DepSpec, path: String, sub: String*) =
    new TrypId(normal, path, sub, true)

  def defaultResolvers = Seq(
      Resolver.sonatypeRepo("releases"),
      Resolver.sonatypeRepo("snapshots"),
      Resolver.sonatypeRepo("staging"),
      Resolver.bintrayRepo("scalaz", "releases"),
      Resolver.jcenterRepo
    )

  def resolvers: Map[String, Seq[Resolver]] = Map()

  // `libraryDependencies += id` for each dep
  // if env is development, devdeps return empty settings
  def apply(name: String): Setts = {
    Seq(Keys.resolvers ++= defaultResolvers ++ this.resolvers.fetch(name)) ++
      (common ++ deps.fetch(name)).map(_.id)
  }

  // ProjectRef instances for each devdep's subprojects
  // if env isn't development, nothing is returned
  def refs(name: String) = {
    (common ++ deps.fetch(name)).map(_.refs).flatten
  }

  val scalazV = "7.1.+"
  val specsV = "3.6.4"

  def common = ids(
    "org.scalaz" %% "scalaz-concurrent" % scalazV,
    "org.scalaz.stream" %% "scalaz-stream" % "0.7.2a"
  )

  def unit = ids(
    "org.scalatest" %% "scalatest" % "2.2.+",
    "org.specs2" %% "specs2-core" % specsV
  )

  def integration = ids(
    "org.scalatest" %% "scalatest" % "2.2.+",
    "org.specs2" %% "specs2-core" % specsV
  )
}

trait Deps
extends DepsBase

object NoDeps
extends Deps
