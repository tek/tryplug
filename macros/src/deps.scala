package tryp

import reflect.macros.Context

import sbt._
import sbt.Keys._

import Types._

// TODO local settingKey in TrypId that stores the dep and is converted to
// libraryDependencies via .init.value

class TrypId(val id: ModuleID, depspec: DepSpec, path: String,
  sub: List[String], dev: Boolean, hook: DepCond,
  cond: Option[SettingKey[Boolean]])
{
  def no = new TrypId(id, depspec, path, sub, false, identity, None)

  def devAllowed = Env.wantDevDep(id.name)

  def development = Env.development && dev && devAllowed

  def dep = {
    if(development) TrypId.empty else {
      val ds = hook(depspec)
      cond.map(a => TrypId.cond(ds, a)).getOrElse(ds)
    }
  }

  def projects = {
    if (sub.isEmpty) List(RootProject(Env.localProject(path)))
    else sub map { n => ProjectRef(Env.localProject(path), n) }
  }

  def refs = {
    if (development) projects.map(a => a: SbtDep)
    else List()
  }

  override def toString = s"TrypId($id, $path, $sub, $dev)"

  def info = {
    if (dev) s"$path${sub mkString("[", ",", "]")}"
    else id.toString
  }
}

object TrypId
{
  def empty = libraryDependencies ++= List()

  def default(id: ModuleID, depspec: DepSpec) =
    new TrypId(id, depspec, "", List(), false, identity, None)

  def plain(depspec: DepSpec) =
    default(invalid, depspec)

  def invalid = "invalid" % "invaild" % "1"

  def cond(ds: DepSpec, c: SettingKey[Boolean]) = {
    libraryDependencies ++= { if(c.value) ds.init.value else List() }
  }
}

trait PluginSpec
{
  def org: String
  def pkg: String
  def label: String
  def current: String
  def invalid: Boolean
}

case class BintrayPluginSpec(user: String, repo: String, org: String,
  pkg: String, label: String, current: String)
extends PluginSpec
{
  def invalid = user == Pspec.invalid
}

case class MavenPluginSpec(org: String, pkg: String, label: String,
  current: String)
extends PluginSpec
{
  def invalid = false
}

case class PluginTrypId(org: String, pkg: String, version: SettingKey[String],
  path: String, sub: List[String], dev: Boolean,
  pspec: Option[Setting[Seq[PluginSpec]]], hook: DepCond,
  cond: Option[SettingKey[Boolean]])
extends TrypId(TrypId.invalid, PluginTrypId.pluginDep(org, pkg, version),
  path, sub, dev, hook, cond)
{
  def aRefs = super.projects

  override def devAllowed = Env.wantDevDep(pkg)

  override def development = super.development && Env.trypDebug.isDefined

  override def no = copy(dev = false)

  override def toString = s"PluginTrypId($org, $pkg)"

  def bintray(user: String, repo: String = "sbt-plugins", name: String = pkg) =
  {
    new PluginTrypId(org, pkg, version, path, sub, dev,
      Some(VersionUpdateKeys.versions +=
        BintrayPluginSpec(user, repo, org, name, version.key.label,
          version.value)),
      hook, cond
    )
  }

  def maven = {
    new PluginTrypId(org, pkg, version, path, sub, dev,
      Some(VersionUpdateKeys.versions +=
        MavenPluginSpec(org, pkg, version.key.label, version.value)),
      hook, cond
    )
  }
}

object PluginTrypId
{
  def pluginDep(org: String, name: String, version: SettingKey[String]) =
      libraryDependencies += Defaults.sbtPluginExtra(
        org % name % version.value,
        (sbtBinaryVersion in update).value,
        (scalaBinaryVersion in update).value
      )
}

object Deps
{
  def ddImpl(c: Context)(id: c.Expr[ModuleID], path: c.Expr[String],
    sub: c.Expr[String]*) =
  {
    import c.universe._
    c.Expr[TrypId] {
      q"""new tryp.TrypId(
        $id, libraryDependencies += $id, $path, List(..$sub), true, identity,
        None
      )
      """
    }
  }

  def dImpl(c: Context)(id: c.Expr[ModuleID]) = {
    import c.universe._
    c.Expr[TrypId] { q"tryp.TrypId.plain(libraryDependencies += $id)" }
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
    TrypId.default(id, libraryDependencies += id)

  implicit class MapOps[A, B](m: Map[A, _ <: Seq[B]]) {
    def fetch(key: A) = m.get(key).toList.flatten
  }

  def ids(i: TrypId*) = List[TrypId](i: _*)

  def deps: Map[String, List[TrypId]] = Map()

  def allDeps = deps ++ ReflectUtilities.allVals[List[TrypId]](this)

  def resolveDeps(name: String) =
    allDeps.fetch(name)

  def dd(id: ModuleID, path: String, sub: String*) = macro Deps.ddImpl

  def d(id: ModuleID) = macro Deps.dImpl

  def plugin(org: String, pkg: String, version: SettingKey[String],
    github: String, sub: List[String] = List(), hook: DepCond = identity,
    cond: Option[SettingKey[Boolean]] = None) =
      PluginTrypId(org, pkg, version, github, sub, true, None, hook, cond)

  def manualDd(normal: DepSpec, path: String, sub: String*) =
    new TrypId(TrypId.invalid, normal, path, sub.toList, true, identity, None)

  def defaultResolvers = List(
      Resolver.bintrayRepo("tek", "releases"),
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
      resolveDeps(name).map(_.dep)
  }

  // ProjectRef instances for each devdep's subprojects
  // if env isn't development, nothing is returned
  def refs(name: String) = {
    resolveDeps(name) flatMap(_.refs)
  }

  // PluginSpec instance for each dep
  def pluginVersions(name: String) = {
    resolveDeps(name)
      .collect { case i: PluginTrypId => i.pspec }
      .flatten
  }
}

trait PluginDeps
extends Deps
{
  import TrypKeys._

  def androidName = "android"
  def trypOrg = "tryp.sbt"
  val huy = "org.scala-android"
  val sdkName = "sbt-android"
  val protifyName = "sbt-android-protify"

  def androidSdk =
    plugin(huy, sdkName, sdkVersion, s"scala-android/$sdkName")
      .bintray("pfn")

  def protify =
    plugin(huy, s"$protifyName", protifyVersion,
      s"scala-android/$protifyName")
        .bintray("pfn")

  def trypBuild =
    plugin(trypOrg, s"tryp-build", trypVersion, "tek/sbt-tryp",
      List("core"))
        .bintray("tek")

  def tryp =
    plugin(trypOrg, s"tryp-$androidName", trypVersion, "tek/sbt-tryp",
      List(androidName))
        .bintray("tek")

  def tryplug =
    plugin(trypOrg, "tryplug", tryplugVersion, "tek/tryplug",
      List("tryplug", "macros"))
        .bintray("tek")

  def coursier =
    plugin("io.get-coursier", "sbt-coursier", coursierVersion,
      "alexarchambault/coursier", cond = Some(useCoursier))
        .maven
}

object NoDeps
extends Deps

object Pspec
{
  val invalid = "[-invalid-]"

  def bintray(c: Context)(user: c.Expr[String], repo: c.Expr[String],
    org: c.Expr[String], pkg: c.Expr[String],
    version: c.Expr[SettingKey[String]]): c.Expr[Any] =
  {
    import c.universe._
    c.Expr(
      q"""
      BintrayPluginSpec($user, $repo, $org, $pkg, $version.key.label,
        $version.value)
      """
    )
  }
}
