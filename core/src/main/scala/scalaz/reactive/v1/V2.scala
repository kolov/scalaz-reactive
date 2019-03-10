package scalaz.reactive.v2

import java.util.concurrent.TimeUnit

import scalaz._
import Scalaz._
import scalaz.zio.stream._
import scalaz.zio.{ stream, _ }
import scalaz.zio.clock.Clock
import scalaz.zio.duration.Duration

object Main extends App {

  type RE      = Any with Clock
  type RIO[+A] = ZIO[Any with Clock, Nothing, A]

  trait Event[+A]
  case class QueueBackedEvent[+A](stream: Stream[RE, Nothing, A]) extends Event[A] {}

  object QueueBackedEvent {

    def create[A]: RIO[(Queue[A], QueueBackedEvent[A])] =
      for {
        q <- Queue.unbounded[A]
        s = stream.Stream.fromQueue(q)
      } yield (q, QueueBackedEvent(s))

    def apply[A](pump: Queue[A] => RIO[Unit]): RIO[QueueBackedEvent[A]] =
      for {
        q <- Queue.unbounded[A]
        s = stream.Stream.fromQueue(q)
        f <- ZIO.forkAll(List(pump(q)))
        _ <- f.await
      } yield QueueBackedEvent(s)
  }

  trait EventInstances {

    implicit def eventMonoid[A] = new Monoid[QueueBackedEvent[A]] {
      override def zero: QueueBackedEvent[A] = QueueBackedEvent(stream.Stream.lift(ZIO.never))
      override def append(f1: QueueBackedEvent[A],
                          f2: => QueueBackedEvent[A]): QueueBackedEvent[A] =
        QueueBackedEvent(f1.stream.merge(f2.stream))
    }

    implicit def eventFunctor = new Functor[QueueBackedEvent] {
      override def map[A, B](fa: QueueBackedEvent[A])(f: A => B): QueueBackedEvent[B] =
        QueueBackedEvent(fa.stream.map(f))
    }
  }

  trait EventSyntax extends EventInstances {
    implicit class EventOps[A](event: QueueBackedEvent[A]) {
      def merge(other: QueueBackedEvent[A]) = event |+| other
    }
  }

  case class Tick(name: String)

  val queue = Queue.unbounded[Tick]

  def tick(q: Queue[Tick], name: String): UIO[Boolean] =
    q.offer(Tick(name))

  def ticks(q: Queue[Tick],
            name: String,
            interval: Duration,
            count: Int): ZIO[Any with Clock, Nothing, Unit] =
    for {
      _ <- tick(q, s"$name $count")
      _ <- ticks(q, name, interval, count + 1).delay(interval)
    } yield ()

  def sink[A](
    e: QueueBackedEvent[A]
  ): ZIO[Any with Clock, Throwable, Unit] =
    e.stream.foreach(t => IO.effect(println(t)))

  val register: (UIO[Tick] => Unit) => Unit = { f: (UIO[Tick] => Unit) =>
    println(f)
  }

  ZIO.effectAsync[Nothing, Tick](register)

  val myAppLogic = for {
    q  <- queue
    st = scalaz.zio.stream.Stream.fromQueue(q)
    f <- ZIO.forkAll(
          List(
            ticks(q, "fast", Duration(1, TimeUnit.SECONDS), 0),
            ticks(q, "slow", Duration(1500, TimeUnit.MILLISECONDS), 0),
            sink(QueueBackedEvent(st))
          )
        )
    _ <- f.await
  } yield ()

  def run(args: List[String]) =
    myAppLogic.fold(_ => 1, _ => 0)

}
