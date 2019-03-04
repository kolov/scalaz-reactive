package scalaz.reactive.v1

import scalaz.Scalaz._
import scalaz._
import scalaz.reactive.v1.V1.Sink.Sink
import scalaz.reactive.v1.V1.{ Event, Future, RIO, Reactive }
import scalaz.zio._
import scalaz.zio.clock.Clock
import scalaz.zio.duration.Duration

object V1 {

  // effect needed by FRP
  trait Frp[F[_]] extends Monad[F] {
    def async[A](a: => A): F[A]
    def delay[A](fa: F[A], duration: Duration): F[A]
    def race[A](f1: F[A], f2: F[A]): F[A]
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

  case class Improving[A: Ord](exact: A, cmp: A => Boolean)

  object Improving {

    def apply[A: Ord](a: A, cmp: A => Boolean) = new Improving[A](a, cmp)

    def exactly[A: Ord](a: A) = Improving[A](a, Ord[A].<=(a, _))

    implicit def equalImproving[A: Equal] = new Equal[Improving[A]] {
      override def equal(a1: Improving[A], a2: Improving[A]): Boolean =
        Equal[A].equal(a1.exact, a2.exact)
    }

    implicit def orderImproving[A: Ord] = new Ord[Improving[A]] {
      override def min(a1: Improving[A], a2: Improving[A]): Improving[A] = minLe(a1, a2)._1
      override def max(a1: Improving[A], a2: Improving[A]): Improving[A] = minLe(a2, a1)._1
      override def <=(a1: Improving[A], a2: Improving[A]): Boolean       = minLe(a1, a2)._2
    }

    def minLe[A](t1: Improving[A], t2: Improving[A]): (Improving[A], Boolean) = ???
  }

  import Improving._

  type Time = Long

  implicit val ordTime = new Ord[Time] {
    override def min(a1: Time, a2: Time): Time   = Math.min(a1, a2)
    override def max(a1: Time, a2: Time): Time   = Math.max(a1, a2)
    override def <=(a1: Time, a2: Time): Boolean = a1 <= a2
  }
  // type FTime = Max (AddBounds (Improving Time))
  // Ftime is time + bounds
  sealed trait FTime

  case object MinBound extends FTime

  case object MaxBound extends FTime

  case class NoBounds(t: Improving[Time]) extends FTime

  implicit val orderFTime = new Ord[FTime] {
    override def min(a1: FTime, a2: FTime): FTime = (a1, a2) match {
      case (MinBound, _)                => MinBound
      case (_, MinBound)                => MinBound
      case (MaxBound, a)                => a
      case (a, MaxBound)                => a
      case (NoBounds(i1), NoBounds(i2)) => NoBounds(Ord[Improving[Time]].min(i1, i2))
    }

    override def max(a1: FTime, a2: FTime): FTime = (a1, a2) match {
      case (MaxBound, _)                => MaxBound
      case (_, MaxBound)                => MaxBound
      case (MinBound, a)                => a
      case (a, MinBound)                => a
      case (NoBounds(i1), NoBounds(i2)) => NoBounds(Ord[Improving[Time]].max(i1, i2))
    }

    override def <=(a1: FTime, a2: FTime): Boolean = (a1, a2) match {
      case (MinBound, _)                => true
      case (MaxBound, _)                => false
      case (_, MinBound)                => false
      case (_, MaxBound)                => true
      case (NoBounds(i1), NoBounds(i2)) => Ord[Improving[Time]].<=(i1, i2)
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
    override def time     = t
    override def value: A = ??? // that's the intention
  }

  case class Future[F[_]: Frp, A](ftv: F[TimedValue[A]])

  implicit class futureOps[F[_]: Frp, A](f: Future[F, A]) {

    def delay(duration: Duration): Future[F, A] =
      Future(Frp[F].delay(f.ftv, duration))
  }

  implicit def monoidFuture[F[_]: Frp, A] = new Monoid[Future[F, A]] {
    override def zero: Future[F, A] = {
      val time: TimedValue[A] = TimeOnly[A](MinBound)
      Future(Frp[F].pure(time))
    }

    override def append(f1: Future[F, A], f2: => Future[F, A]): Future[F, A] = {
      val t1: F[FTime] = f1.ftv.map(_.time)
      val t2: F[FTime] = f2.ftv.map(_.time)
      println(s"$t1,$t2")
      val minLE: F[(FTime, Boolean)] = t1.map(ft => (ft, true)) // FIXME

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

  case class Reactive[F[_]: Frp, A](head: A, tail: Event[F, A])

  case class Event[F[_]: Frp, A](value: Future[F, Reactive[F, A]]) { self =>
    def delay(interval: Duration): Event[F, A] = Event(value.delay(interval))
  }

  type RIO[A] = ZIO[Clock, Throwable, A]

  implicit val frpIO = new Frp[RIO] {
    import scalaz.zio._
    override def async[A](a: => A): RIO[A]                        = ZIO.effect(a)
    override def delay[A](fa: RIO[A], duration: Duration): RIO[A] = fa.delay(duration)
    override def bind[A, B](fa: RIO[A])(f: A => RIO[B]): RIO[B]   = fa.flatMap(f)
    override def point[A](a: => A): RIO[A]                        = ZIO.effect(a)
    override def race[A](f1: RIO[A], f2: RIO[A]): RIO[A]          = f1.race(f2)

  }

  /// where to put this?
  def liftTimed[A](a: => A): RIO[TimedValue[A]] =
    frpIO.async(new TimedValue[A] {
      override def time: V1.FTime = NoBounds(Improving.exactly(System.currentTimeMillis()))
      override def value: A       = a
    })

  object Sink {

    type Sink[A] = A => RIO[Unit]

    def sinkR[A, B](sink: Sink[A], r: Reactive[RIO, A]): RIO[Unit] =
      sink(r.head).flatMap(_ => sinkE(sink, r.tail))

    def sinkE[A, B](sink: Sink[A], e: Event[RIO, A]): RIO[Unit] =
      e.value.ftv.flatMap { tv =>
        sinkR(sink, tv.value)
      }

  }

}

object Main extends App {

  case class Tick(name: String)

  def ticks(interval: Duration, count: Int): Event[RIO, Tick] = {

    def r: Reactive[RIO, Tick] =
      Reactive(Tick(s"N$count"), ticks(interval, count + 1).delay(interval))
    val ftv: RIO[V1.TimedValue[Reactive[RIO, Tick]]] = V1.liftTimed(r)
    val f: Future[RIO, Reactive[RIO, Tick]]          = Future(ftv)
    Event(f)
  }

  val sink: Sink[Tick] =
    ((t: Tick) => V1.frpIO.async(println(s"tick ${t.name}")))

  val myAppLogic =
    V1.Sink.sinkE(
      sink,
      ticks(Duration.Finite(10000), 0)
    )

  def run(args: List[String]) =
    myAppLogic.fold(_ => 1, _ => 0)

}
