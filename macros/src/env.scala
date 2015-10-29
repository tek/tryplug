package tryp

import sbt._

object Types
{
  type DepSpec = Setting[Seq[ModuleID]]
  type Setts = Seq[Setting[_]]
}
import Types._

object Env
{
  val current = sys.props.getOrElse("env", "development")

  def development = current == "development"

  val projectBaseEnvVar = "TRYP_SCALA_PROJECT_DIR"

  val projectBaseProp = "tryp.projectsdir"

  lazy val projectBasePath = sys.props.getOrElse(
    projectBaseProp,
    sys.env.get(projectBaseEnvVar)
      .getOrElse(sys.error(
        s"Need to pass -D$projectBaseProp or set $$$projectBaseEnvVar"))
  )

  lazy val projectBase = new File(projectBasePath)

  def cloneRepo(path: String, dirname: String) = {
    s"hub clone $path ${Env.projectBase}/$dirname" !
  }

  def localProject(path: String) = {
    val dirname = path.split("/").last
    val localPath = Env.projectBase / dirname
    if (!localPath.isDirectory) cloneRepo(path, dirname)
    localPath
  }

  def trypDebug = sys.env.get("TRYP_DEBUG")
}
