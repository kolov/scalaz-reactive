package scalaz.reactive

import scalaz._, Scalaz._
import scalaz.zio.{ Queue, UIO, ZIO, stream }
import scalaz.zio.stream.Stream

trait Event[+A] {
  def stream: Stream[RE, Nothing, A]
}

object Event {

  type Producer[A] = (A => UIO[Unit]) => RIO[Unit]

  private case class EventQ[+A](stream: Stream[RE, Nothing, A]) extends Event[A]

  def apply[A](stream: Stream[RE, Nothing, A]): Event[A] = EventQ(stream)

  def fromQueue[A]: RIO[(Queue[A], Event[A])] =
    for {
      q <- Queue.unbounded[A]
      s = stream.Stream.fromQueue(q)
    } yield (q, EventQ(s))

  def fromProducer[A](pump: Producer[A]): RIO[Event[A]] =
    for {
      q <- Queue.unbounded[A]
      s = stream.Stream.fromQueue(q)
      _ <- pump(q.offer(_).map(_ => ())).fork
    } yield EventQ(s)

//  // chapter 12
//  //accumR :: a → Event (a → a) → Reactive a
//  def accumR[A](a: A)(e: Event[A => A]): Reactive[A] = Reactive(a, accumE(a)(e))
//
  //  accumE :: a → Event (a → a) → Event a
  def accumE[A](a: A)(e: Event[A => A]): RIO[Event[A]] = {

    val producer: Producer[A] = { emit =>
      ???
      }

    Event.fromProducer(producer)

  }

  def joinMaybes[A](e: Event[Option[A]]): Event[A] =
    Event(e.stream.filter(_.isDefined).map(_.get))
}

trait EventInstances {

  implicit def eventMonoid[A] = new Monoid[Event[A]] {
    override def zero: Event[A] = Event(stream.Stream.lift(ZIO.never))
    override def append(f1: Event[A], f2: => Event[A]): Event[A] =
      Event(f1.stream.merge(f2.stream))
  }

  implicit def eventFunctor = new Functor[Event] {
    override def map[A, B](fa: Event[A])(f: A => B): Event[B] =
      Event(fa.stream.map(f))
  }
}

object EventInstances extends EventInstances

trait EventSyntax extends EventInstances {
  implicit class EventOps[A](event: Event[A]) {
    def merge(other: Event[A])      = event |+| other
    def map[B](f: A => B): Event[B] = Functor[Event[A]].map(event)(f)
  }
}

object EventSyntax extends EventSyntax
