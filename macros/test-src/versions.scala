package tryp

import scala.concurrent.{Future, ExecutionContext}
import scala.concurrent.duration._

import cats.instances.future._
import sbt.Logger
import org.specs2._
import org.specs2.concurrent.ExecutionEnv

trait VersionSpec
extends Specification
with matcher.FutureMatchers
{
  val log = Logger.Null

  val meta = VersionsMeta(log, None, Map(), Map(), "2.12", "1.0")

  def run(plugin: SemanticVersion => PluginVersion, current: String)
  (implicit ec: ExecutionContext)
  : Future[Either[String, Int]] =
    SemanticVersion.parse(current) match {
      case Right(v) =>
        for {
          result <- Versions.newVersion(plugin(v)).runA(meta).map {
            case Some(v) => Right(v.major.toInt)
            case None => Left("no new version")
          }
        } yield result
      case Left(err) =>
        Future.successful(Left(err))
    }
}

class MavenSpec(implicit ee: ExecutionEnv)
extends VersionSpec
{
  def is = s2"""
  low $low
  high $high
  """

  def go(current: String) = run(v => PluginVersion(MavenSource, "org.ensime", "sbt-ensime", "v", v), current)

  def high = go("10.0.0") must beLeft.await

  def low = go("0.1.0") must beRight(be_>=(2)).await
}

class BintraySpec(implicit ee: ExecutionEnv)
extends VersionSpec
{
  def is = s2"""
  low $low
  high $high
  """

  def go(current: String) =
    run(v => PluginVersion(BintraySource("tek", "sbt-plugins"), "tryp.sbt", "tryp-build", "v", v), current)

  def high = go("1000") must beLeft.await(1, 3.seconds)

  def low = go("1") must beRight(be_>=(95)).await(1, 3.seconds)
}
