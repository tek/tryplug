package tryp

import sbt.Logger

import org.specs2._

class V(implicit val log: Logger)
extends Versions

trait VersionSpec
extends Specification
with matcher.TaskMatchers
{
  implicit lazy val l = Logger.Null

  lazy val v = new V
}

class MavenSpec
extends VersionSpec
{
  def is = s2"""
  low $low
  high $high
  """

  def go(current: String) = {
    val spec = MavenPluginSpec("org.ensime", "sbt-ensime", "v", current)
    v.updateTask(spec).map(_.minor.toInt)
  }

  def high = go("10.0.0") must returnValue(-1)

  def low = go("0.1.0") must returnValue(be_>=(5))
}

class BintraySpec
extends VersionSpec
{
  def is = s2"""
  low $low
  high $high
  """

  def go(current: String) = {
    val spec = BintrayPluginSpec("tek", "sbt-plugins", "tryp.sbt",
      "tryp-build", "v", current)
    v.updateTask(spec).map(_.major.toInt)
  }

  def high = go("1000") must returnValue(-1)

  def low = go("1") must returnValue(be_>=(95))
}
