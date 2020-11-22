package zio.redis

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._
import zio.ZIO

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput, Mode.AverageTime))
@OutputTimeUnit(TimeUnit.SECONDS)
@Measurement(iterations = 10)
@Warmup(iterations = 5)
@Fork(2)
class SetBenchmarks {
  import BenchmarkRuntime._
  import BenchmarksUtils._
  import RedisClients._

  @Param(Array("500"))
  private var count: Int = _

  private var items: List[String] = _

  @Setup(Level.Trial)
  def setup(): Unit =
    items = (0 to count).toList.map(_.toString)

  @Benchmark
  def laserdisc(): Unit = {
    import _root_.laserdisc.fs2._
    import _root_.laserdisc.{ all => cmd, _ }
    import cats.instances.list._
    import cats.syntax.foldable._

    unsafeClientRun[LaserDiscClient](c => items.traverse_(i => c.send(cmd.set(Key.unsafeFrom(i), i))))
  }

  @Benchmark
  def rediculous(): Unit = {
    import cats.implicits._
    import io.chrisdavenport.rediculous._
    unsafeClientRun[RediculousClient](c => items.traverse_(i => RedisCommands.set[RedisIO](i, i).run(c)))
  }

  @Benchmark
  def redis4cats(): Unit = {
    import cats.instances.list._
    import cats.syntax.foldable._
    unsafeClientRun[Redis4CatsClient[String]](c => items.traverse_(i => c.set(i, i)))
  }

  @Benchmark
  def zio(): Unit = zioUnsafeRun(ZIO.foreach_(items)(i => set(i, i)))
}
