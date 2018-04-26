enablePlugins(Tryplug)
ensimeVersion in ThisBuild := "2.5.1"
releaseVersion in ThisBuild := "1.0.8"

val core = pluginSubProject("core").settings(deps := tryp.PluginDeps)

val root = pluginRoot("root").settings(deps := tryp.PluginDeps)

val testDepTask: Def.Initialize[Task[Unit]] = Def.task {
  val coreDeps = (libraryDependencies in core).value
  val rootDeps = (libraryDependencies in root).value
  val has = (deps: Seq[ModuleID]) => (name: String) => deps.exists(_.name == name)
  val coreHas = has(coreDeps)
  val rootHas = has(rootDeps)
  if (!(coreHas("sbt-release") && coreHas("sbt-ensime") && rootHas("sbt-ensime")))
    sys.error("missing dependencies")
  ()
}

TaskKey[Unit]("testDep") := testDepTask.value
