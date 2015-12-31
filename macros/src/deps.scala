package tryp

import reflect.macros.Context

import sbt._
import sbt.Keys._

import Types._

class TrypId(val id: ModuleID, depspec: DepSpec, path: String,
  sub: List[String], dev: Boolean)
{
  def no = new TrypId(id, depspec, path, sub, false)

  def devBlocked = !Env.wantDevDep(id.name)

  def development = Env.development && dev && !devBlocked

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

object TrypId
{
  def empty = libraryDependencies ++= List()

  def plain(depspec: DepSpec) = new TrypId(invalid, depspec, "", List(), false)

  def invalid = "invalid" % "invaild" % "1"
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
  path: String, sub: List[String], dev: Boolean = true,
  pspec: Option[Setting[Seq[PluginSpec]]] = None)
extends TrypId(TrypId.invalid, PluginTrypId.pluginDep(org, pkg, version),
  path, sub, dev)
{
  def aRefs = super.projects

  override def development = super.development && Env.trypDebug.isDefined

  override def no = copy(dev = false)

  def bintray(user: String, repo: String = "sbt-plugins", name: String = pkg) =
  {
    new PluginTrypId(org, pkg, version, path, sub, dev,
      Some(VersionUpdateKeys.versions +=
        BintrayPluginSpec(user, repo, org, name, version.key.label,
          version.value)
      )
    )
  }

  def maven = {
    new PluginTrypId(org, pkg, version, path, sub, dev,
      Some(VersionUpdateKeys.versions +=
        MavenPluginSpec(org, pkg, version.key.label, version.value)
      )
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
        $id, libraryDependencies += $id, $path, List(..$sub), true
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

  def plugin(org: String, pkg: String, version: SettingKey[String],
    github: String, sub: List[String] = List()) =
      PluginTrypId(org, pkg, version, github, sub)

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
      case i: PluginTrypId ⇒ i.pspec
    } flatten
  }

  val scalazV = "7.2.+"

  // this dep cannot be made +, because specs2's versioning scheme is malicious
  // there are releases with revision 3.6-201505... which pertain to the 3.6
  // line yet register as newer than 3.6.x
  val specsV = "3.6.+"

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

trait PluginDeps
extends Deps
{
  override val scalazV = "7.1.+"

  import TrypKeys._

  def androidName = "android"
  def trypOrg = "tryp.sbt"
  val huy = "com.hanhuy.sbt"
  val sdkName = "android-sdk-plugin"
  val protifyName = "protify"

  def androidSdk =
    plugin(huy, sdkName, sdkVersion, s"pfn/$sdkName")
      .bintray("pfn")

  def protify =
    plugin(huy, s"android-$protifyName", protifyVersion, s"pfn/$protifyName")
      .bintray("pfn")

  def tryp =
    plugin(trypOrg, s"tryp-$androidName", trypVersion, "tek/sbt-tryp",
      List(androidName))
        .bintray("tek")

  def tryplug =
    plugin(trypOrg, "tryplug", tryplugVersion, "tek/tryplug",
      List("tryplug", "macros"))
        .bintray("tek")
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
