package tryp

import scala.concurrent.ExecutionContext.Implicits.global

import sbt.Logger

import org.specs2._

class V(implicit val log: Logger)
extends Versions

trait VersionSpec
extends Specification
with matcher.FutureMatchers
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
    v.updateFuture(spec).map(_.minor.toInt)
  }

  def high = go("10.0.0") must be_==(-1).await

  def low = go("0.1.0") must be_>=(5).await
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
    v.updateFuture(spec).map(_.major.toInt)
  }

  def high = go("1000") must be_==(-1).await

  def low = go("1") must be_>=(95).await
}
