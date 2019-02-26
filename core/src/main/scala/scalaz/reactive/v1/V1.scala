package scalaz.reactive.v1
import scalaz._
import Scalaz._
import scalaz.reactive.Sync

object V1 {

  trait Improving[A] {
    def exact: A
    def compare(other: A)(implicit ord: Order[A]): Ordering
  }

  type Time = Long

  trait FTime
  case object MinTime extends FTime
  case object MaxTime extends FTime
  case class ImprovingTime(f: Improving[Time]) extends FTime

  implicit val orderTime = new Order[FTime] {
    override def order(x: FTime, y: FTime): Ordering = (x, y) match {
      case (MinTime, _) => Ordering.LT
      case (_, _)       => ???
    }
  }

  trait Sync[F[_]] extends Monad[F] {
    def never[A]: F[A]
  }

  object Sync {
    def apply[F[_]](implicit F: Sync[F]): Sync[F] = F
  }

  case class TimedValue[A](t: FTime, a: A)

  case class Future[F[_]: Sync, A](ftv: F[TimedValue[A]])

  implicit def monoidFuture[F[_]: Sync, A] = new Monoid[Future[F[_], A]] {
    override def zero: Future[F[_], A] = Future(Sync[F].never)
    override def append(f1: Future[F[_], A],
                        f2: => Future[F[_], A]): Future[F[_], A] = {
      val t1 = f1.ftv.map(_.t)
      val t2 = f2.ftv.map(_.t)
      val minLE: IO1[(Improving[Time], Boolean)] =
        Improving.minLe(t1, t2, Improving.afterNow)
      val futureTimedValue: IO1[TimedValue[A]] = minLE.flatMap {
        case (it, isLe) =>
          if (isLe)
            f1.ftv.map(tv => TimedValue(it, tv.value))
          else
            f2.ftv.map(tv => TimedValue(it, tv.value))
      }
      Future(futureTimedValue)
    }
  }

}

object Main extends App {
  println("Hello")
}
