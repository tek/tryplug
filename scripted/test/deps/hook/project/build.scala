package tryp

import sbt._
import sbt.Keys._

import Types._

object B
extends Build
with Tryplug
{
  val v = settingKey[String]("version")
  val useCondPos = settingKey[Boolean]("condPos")
  val useCondNeg = settingKey[Boolean]("condNeg")

  def testDep: Def.Initialize[Task[Unit]] = Def.task {
    val org = (libraryDependencies in core).value
    val has = (name: String) => org.exists(_.name == name)
    if (has("neg") || !has("pos") || has("condNeg") || !has("condPos"))
      error("Conditional dependencies failed")
    ()
  }

  lazy val core = pluginSubProject("core")
    .settings(
      v := "1.0.0",
      useCondPos := true,
      useCondNeg := false
    )

  lazy val root = pluginRoot("root")
    .settings(
      TaskKey[Unit]("testDep") <<= testDep
    )

  def positive(d: DepSpec): DepSpec = {
    libraryDependencies ++= d.init.value
  }

  def negative(d: DepSpec): DepSpec = {
    libraryDependencies ++= List()
  }

  override object deps
  extends PluginDeps
  {
    val pos = plugin("org", "pos", v, "org/pos", hook = positive _)
    val neg = plugin("org", "neg", v, "org/neg", hook = negative _)
    val condPos = plugin("org", "condPos", v, "org/cond-pos",
      cond = Some(useCondPos))
    val condNeg = plugin("org", "condNeg", v, "org/cond-neg",
      cond = Some(useCondNeg))

    lazy val core = ids(pos, neg, condPos, condNeg)
  }
}
