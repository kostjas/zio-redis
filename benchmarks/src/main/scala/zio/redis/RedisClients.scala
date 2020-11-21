package zio.redis

import cats.effect.{ Blocker, Resource, IO => CatsIO }
import dev.profunktor.redis4cats.RedisCommands
import dev.profunktor.redis4cats.data.RedisCodec
import fs2.io.tcp.SocketGroup
import io.chrisdavenport.rediculous.{ Redis, RedisConnection }
import io.lettuce.core.ClientOptions
import laserdisc.fs2.RedisClient
import zio.redis.BenchmarkRuntime.{ RedisHost, RedisPort }

object RedisClients {
  import BenchmarkRuntime.cs
  import BenchmarkRuntime.timer

  type RedisIO[A] = Redis[CatsIO, A]

  type Redis4CatsClient[V] = RedisCommands[CatsIO, String, V]
  type LaserDiskClient     = RedisClient[CatsIO]
  type RediculousClient    = RedisConnection[CatsIO]

  trait QueryUnsafeRunner[F] {
    def unsafeRun(f: F => CatsIO[Unit]): Unit
  }

  implicit object LaserDiskClientRunner extends QueryUnsafeRunner[LaserDiskClient] {
    override def unsafeRun(f: LaserDiskClient => CatsIO[Unit]): Unit = laserDiskConnection.use(f).unsafeRunSync
  }

  implicit object RedicoulusClientRunner extends QueryUnsafeRunner[RediculousClient] {
    override def unsafeRun(f: RediculousClient => CatsIO[Unit]): Unit = redicoulusConnection.use(f).unsafeRunSync
  }

  implicit object Redis4CatsClientRunnerString extends QueryUnsafeRunner[Redis4CatsClient[String]] {
    override def unsafeRun(f: Redis4CatsClient[String] => CatsIO[Unit]): Unit =
      redis4CatsConnectionString.use(f).unsafeRunSync
  }

  implicit object Redis4CatsClientRunnerLong extends QueryUnsafeRunner[Redis4CatsClient[Long]] {
    override def unsafeRun(f: Redis4CatsClient[Long] => CatsIO[Unit]): Unit =
      redis4CatsConnectionLong.use(f).unsafeRunSync
  }

  private val redicoulusConnection: Resource[CatsIO, RediculousClient] =
    for {
      blocker <- Blocker[CatsIO]
      sg      <- SocketGroup[CatsIO](blocker)
      c       <- RedisConnection.queued[CatsIO](sg, RedisHost, RedisPort, maxQueued = 10000, workers = 2)
    } yield c

  import _root_.laserdisc.auto.autoRefine
  private val laserDiskConnection: Resource[CatsIO, LaserDiskClient] = RedisClient.to(RedisHost, RedisPort)

  import dev.profunktor.redis4cats.Redis
  import dev.profunktor.redis4cats.effect.Log.NoOp.instance
  private val redis4CatsConnectionString: Resource[CatsIO, RedisCommands[CatsIO, String, String]] =
    Redis[CatsIO].utf8(s"redis://$RedisHost:$RedisPort")

  private val redis4CatsConnectionLong: Resource[CatsIO, RedisCommands[CatsIO, String, Long]] = {
    import dev.profunktor.redis4cats.codecs.Codecs
    import dev.profunktor.redis4cats.codecs.splits._

    val longCodec: RedisCodec[String, Long] = Codecs.derive(RedisCodec.Utf8, stringLongEpi)
    Redis[CatsIO].withOptions(s"redis://$RedisHost:$RedisPort", ClientOptions.create(), longCodec)
  }
}