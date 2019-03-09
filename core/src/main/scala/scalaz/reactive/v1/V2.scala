package scalaz.reactive.v2

import java.util.concurrent.TimeUnit

import scalaz.zio._
import scalaz.zio.clock.Clock
import scalaz.zio.duration.Duration

object Main extends App {

  case class Tick(name: String)
  private val duration = Duration(1, TimeUnit.SECONDS)

  val queue = Queue.unbounded[Tick]

  def tick(q: Queue[Tick], name: String): UIO[Boolean] =
    q.offer(Tick(name))

  def ticks(q: Queue[Tick], interval: Duration, count: Int): ZIO[Any with Clock, Nothing, Unit] =
    for {
      _ <- tick(q, s"1-$count")
      _ <- ticks(q, duration, count + 1).delay(duration)
    } yield ()

  def sink[A](
    s: scalaz.zio.stream.Stream[Any with Clock, Throwable, A]
  ): ZIO[Any with Clock, Throwable, Unit] =
    s.foreach(t => IO.effect(println(t)))

  val myAppLogic = for {
    q  <- queue
    st = scalaz.zio.stream.Stream.fromQueue(q)
    f <- ZIO.forkAll(
          List(
            ticks(q, duration, 0),
            sink(st)
          )
        )
    _ <- f.await
  } yield ()

  def run(args: List[String]) =
    myAppLogic.fold(_ => 1, _ => 0)

}
