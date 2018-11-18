package scalaz.tagless
import scalaz.Monad
import scalaz.Scalaz._
import scalaz.tagless.Frp.Future
import scalaz.tagless.types.IO1
import scalaz.zio.{App, IO}

import scala.concurrent.duration.{Duration, _}
import scala.language.postfixOps

object TwoTickers1 extends App {

  import Ops._

  case class Tick(name: String)

  class Program[F[_]](implicit val frp: Frp[F], m: Monad[F]) {

    def ticks(interval: Duration, name: String): F[Event[F, Tick]] = {

      def tail(): F[Event[F, Tick]] =
        ticks(interval, name).flatMap(tss => frp.delay(tss, interval))

      val x: F[Reactive[F, Tick]] = for {
        head <- m.point(Tick(name))
        tail <- tail()
      } yield Reactive(head, tail)
      val z: Future[F, Reactive[F, Tick]] =
        frp.now.flatMap(t => x.map(r => (t, r)))
      m.point(Event(z))
    }

    def myAppLogic: F[Unit] = {
      val sink: Tick => F[Unit] = (t: Tick) =>
        frp.pure(println(s"tick ${t.name}"))

      val eventA: F[Event[F, Tick]] = ticks(1.2 second, "a")
      val eventB: F[Event[F, Tick]] = ticks(2.1 second, "b")
      val merged: F[Event[F, Tick]] =
        m.bind(eventA)(a => m.bind(eventB)(b => a.merge(b)))

      merged.flatMap(frp.sinkE(sink, _))
    }
  }

  implicit val frp = FrpIo
  implicit val m: Monad[IO1] = monadIo

  override def run(args: List[String]): IO[Nothing, TwoTickers1.ExitStatus] = {
    val logic: IO1[Unit] = new Program().myAppLogic
    val attempted: IO[Nothing, Either[Void, Unit]] = logic.attempt
    attempted.map(_.fold(_ => 1, _ => 0)).map(ExitStatus.ExitNow(_))
  }
}
