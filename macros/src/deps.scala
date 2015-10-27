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

  override def toString = s"TrypId($plainId, $path, $sub, $dev)"
}

case class PluginSpec(user: String, pkg: String, label: String,
  current: String)
{
  def invalid = user == Pspec.invalid
}

class PluginTrypId(plainId: DepSpec, path: String, sub: Seq[String],
  dev: Boolean, val version: Setting[Seq[PluginSpec]])
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

  def pdImpl(c: Context)(org: c.Expr[String], pkg: c.Expr[String],
    version: c.Expr[SettingKey[String]], bintray: c.Expr[String],
    github: c.Expr[String], sub: c.Expr[String]*) =
  {
    import c.universe._
    c.Expr[PluginTrypId] {
      val vspec = Pspec.create(c)(bintray, pkg, version)
      q"""new tryp.PluginTrypId(
        plugin($org, $pkg, $version), $github, Seq(..$sub), true,
          VersionUpdateKeys.versions += $vspec
      )
      """
    }
  }
}

trait Deps
{
  implicit def ModuleIDtoTrypId(id: ModuleID) =
    new TrypId(libraryDependencies += id, "", List(), false)

  implicit class MapOps[A, B](m: Map[A, _ <: Seq[B]]) {
    def fetch(key: A) = m.get(key).toSeq.flatten
  }

  def ids(i: TrypId*) = Seq[TrypId](i: _*)

  def deps: Map[String, Seq[TrypId]] = Map(
    "unit" → unit,
    "integration" → integration
  )

  def dd(id: ModuleID, path: String, sub: String*) = macro Deps.ddImpl

  def d(id: ModuleID) = macro Deps.dImpl

  def pd(org: String, pkg: String, version: SettingKey[String],
    bintray: String, github: String, sub: String*) = macro Deps.pdImpl

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
    (common ++ deps.fetch(name)) flatMap(_.refs)
  }

  // PluginSpec instance for each dep
  def pluginVersions(name: String) = {
    deps fetch(name) collect {
      case i: PluginTrypId ⇒ i.version
    }
  }

  val scalazV = "7.1.+"

  // this dep cannot be made +, because specs2's versioning scheme is malicious
  // there are releases with revision 3.6-201505... which pertain to the 3.6
  // line yet register as newer than 3.6.x
  val specsV = "3.6.5"

  def common = ids(
    "org.scalaz" %% "scalaz-concurrent" % scalazV,
    "org.scalaz.stream" %% "scalaz-stream" % "+"
  )

  def testCommon = ids(
    "org.scalatest" %% "scalatest" % "2.2.+",
    "org.specs2" %% "specs2-core" % specsV
  )

  def unit = testCommon

  def integration = testCommon
}

object NoDeps
extends Deps

object Pspec
{
  val invalid = "[-invalid-]"

  def create(c: Context)(user: c.Expr[String], pkg: c.Expr[String],
    version: c.Expr[SettingKey[String]]): c.Expr[Any] =
  {
    import c.universe._
    c.Expr(
      q"""
      PluginSpec($user, $pkg, $version.key.label, $version.value)
      """
    )
  }
}
