package scalaz.reactive.v1

import scalaz.Scalaz._
import scalaz._
import scalaz.reactive.v1.V1.{Event, Improving, Ord, Reactive}
import scalaz.zio.UIO
import scalaz.zio.duration.Duration

object V1 {

  trait Frp[F[_]] extends Monad[F] {
    def never[A]: F[A]
    def delay[A](fa: F[A], duration: Duration): F[A]
  }

  object Frp {
    def apply[F[_]](implicit F: Frp[F]): Frp[F] = F
  }

  trait Ord[A] {
    def min(a1: A, a2: A): A
    def max(a1: A, a2: A): A
    def <=(a1: A, a2: A): Boolean
  }

  object Ord {
    implicit def apply[A: Ord]: Ord[A] = implicitly[Ord[A]]
  }

//  import Ord._

  case class Improving[A : Ord](exact: A, cmp: A => Boolean)


  object Improving {

    def apply[A : Ord](a:A, cmp: A => Boolean) = new Improving[A](a, cmp)

    def exactly[A: Ord](a: A) = Improving[A](a, Ord[A].<=(a, _))

    implicit def equalImproving[A: Equal] = new Equal[Improving[A]] {
      override def equal(a1: Improving[A], a2: Improving[A]): Boolean =
        Equal[A].equal(a1.exact, a2.exact)
    }

    implicit def orderImproving[A: Ord] = new Ord[Improving[A]] {
      override def min(a1: Improving[A], a2: Improving[A]): Improving[A] = minLe(a1,a2)._1
      override def max(a1: Improving[A], a2: Improving[A]): Improving[A] = minLe(a2,a1)._1
      override def <=(a1: Improving[A], a2: Improving[A]): Boolean       = minLe(a1,a2)._2
    }

    def fminLe[F[_], A](t1: F[Improving[A]], t2: F[Improving[A]]): F[(Improving[A], Boolean)] = ???
    def minLe[A](t1: Improving[A], t2: Improving[A]): (Improving[A], Boolean)                 = ???
  }

  type Time = Long
  // type FTime = Max (AddBounds (Improving Time))
  // Ftime is time + bounds
  trait FTime

  case object MinBound extends FTime

  case object MaxBound extends FTime

  case class NoBounds(t: Improving[Time]) extends FTime

  implicit val orderTime = new Order[FTime] {
    override def order(x: FTime, y: FTime): Ordering = (x, y) match {
      case (MinBound, _) => Ordering.LT
      case (_, _)        => ???
    }
  }

  // Timed value = always has FTime, value revealed if exact time
  sealed trait TimedValue[A] {
    def time: FTime
    def value: A
  }

  case class TimeAndValue[A](t: FTime, a: A) extends TimedValue[A] {
    override def time  = t
    override def value = a
  }

  case class TimeOnly[A](t: FTime) extends TimedValue[A] {
    override def time  = t
    override def value = ??? // that's the intention
  }

  case class Future[F[_]: Frp, A](ftv: F[TimedValue[A]])

  implicit class futureOps[F[_]: Frp, A](f: Future[F, A]) {
    def delay(duration: Duration)
  }

  implicit def monoidFuture[F[_]: Frp, A] = new Monoid[Future[F, A]] {
    override def zero: Future[F, A] = Future(Frp[F].pure(TimeOnly[A](MinBound)))

    override def append(f1: Future[F, A], f2: => Future[F, A]): Future[F, A] = {
      val t1: F[FTime] = f1.ftv.map(_.time)
      val t2: F[FTime] = f2.ftv.map(_.time)
      val minLE: F[(FTime, Boolean)] =
        Improving.minLe(t1, t2)

      val futureTimedValue: F[TimedValue[A]] = minLE.flatMap {
        case (it, isLe) =>
          if (isLe)
            f1.ftv.map(tv => TimeAndValue(it, tv.value))
          else
            f2.ftv.map(tv => TimeAndValue(it, tv.value))
      }
      Future(futureTimedValue)
    }
  }

  case class Reactive[+A](head: A, tail: Event[A])

  case class Event[+A](value: Future[UIO, Reactive[A]]) { self =>
    def delay(interval: Duration): Event[A] = Event(value.delay(interval))
  }

}

object Main extends App {

  case class Tick(name: String)

  def ticks(interval: Duration, name: String): Event[Tick] =
    Event(Time.now.map { t =>
      (t, Reactive(Tick(name), ticks(interval, name).delay(interval)))
    })

  println("Hello")
}
