package scalaz.reactive.examples

import java.util.concurrent.TimeUnit

import scalaz.reactive.Event.Producer
import scalaz.reactive._
import scalaz.zio._
import scalaz.zio.clock.Clock
import scalaz.zio.duration.Duration

import scala.language.postfixOps

object TwoTickers extends App {

  case class Tick(name: String)

  val queue = Queue.unbounded[Tick]

  def ticks(emit: Tick => UIO[Unit],
            name: String,
            interval: Duration,
            count: Int): ZIO[Any with Clock, Nothing, Unit] =
    for {
      _ <- emit(Tick(s"$name $count"))
      _ <- ticks(emit, name, interval, count + 1).delay(interval)
    } yield ()

  def ticksPump(name: String, interval: Duration): Producer[Tick] = { emit =>
    ticks(emit, name, interval, 0)
  }

  def sink[A](
    e: Event[A]
  ): ZIO[Any with Clock, Throwable, Unit] =
    e.stream.foreach(t => IO.effect(println(t)))

  val myAppLogic = for {
    a <- Event.fromProducer(ticksPump("a", Duration(1000, TimeUnit.MILLISECONDS)))
    b <- Event.fromProducer(ticksPump("b", Duration(1100, TimeUnit.MILLISECONDS)))
    _ <- sink(a.merge(b))
  } yield ()

  def run(args: List[String]) =
    myAppLogic.fold(_ => 1, _ => 0)

}
