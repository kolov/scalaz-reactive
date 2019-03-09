package scalaz.reactive.v2

import java.util.concurrent.TimeUnit.SECONDS

import scalaz.zio.{ stream, _ }
import scalaz.zio.clock.Clock
import scalaz.zio.duration.Duration
import scalaz.zio.stream._

object Main extends App {

  case class Tick(name: String)

  val queue = Queue.unbounded[Tick]
  queue.map(q => q.offer(Tick("start")))
  val stream = queue.map(Stream.fromQueue(_))

  val sink: ZIO[Any, Throwable, Unit] = stream.flatMap(s => s.foreach(t => IO.effect(println(t))))

  val duration: Duration = Duration(1, SECONDS)

  def ticks(count: Int): ZIO[Any with Clock, Throwable, Unit] =
    for {
      _ <- queue.map(q => q.offer(Tick(s"N$count")))
      _ = println("1")
      _ <- ZIO.effect(()).delay(duration)
    } yield ()

  val myAppLogic: ZIO[Any with Clock, Nothing, UIO[Exit[Throwable, List[Unit]]]] = for {
    y <- ZIO.forkAll(
          List(
            ticks(0),
            sink,
            ZIO.effect(println("hello")),
            ZIO.effect(()).delay(Duration(10, SECONDS)).map(_ => println("10 sec passed"))
          )
        )

  } yield y.await

  def run(args: List[String]) =
    myAppLogic.flatMap(x => x).fold(_ => { println("???"); 1 }, a => { println(s" $a !!!"); 0 })
}
