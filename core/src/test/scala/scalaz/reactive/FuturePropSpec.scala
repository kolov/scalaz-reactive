package scalaz.reactive

import java.text.SimpleDateFormat

import org.scalacheck.Gen
import org.scalacheck.Prop.forAll
import org.specs2.matcher.MustMatchers
import org.specs2.{ScalaCheck, Specification}
import scalaz._
import Scalaz._
import scalaz.reactive.io.types.IO1
import scalaz.zio.{IO, RTS}

import scala.concurrent.duration._

class FuturePropSpec
    extends Specification
    with ScalaCheck
    with FutureInstances
    with MustMatchers {

  import scalaz.reactive.io.instances._

  val rts = new RTS {}

  val DateFormat = "HH:mm:ss.SSS"
  def now = new SimpleDateFormat(DateFormat).format(System.currentTimeMillis())

  def is = "FutureSpec".title ^ s2""" 
   Appends two slow futures. ${appendTwoSlowFutures}

    """
//   Appends two exact futures. ${appendTwoExactFutures}
//   Appends exact + slow futures. ${appendExactToSlowFutures}

  // generate values for the a:A
  def timeGen: Gen[Time.T] =
    Gen.choose[Long](0, 100).map(t => Time.T(t))

  def exactFuture =
    for {
      t <- timeGen
    } yield Future[IO1, Int](t, 0)

  def slowFuture =
    for {
      num <- Gen.choose[Int](0, 100)
      delay <- Gen.choose[Int](300, 500)
    } yield
      (delay, Future[IO1, Int](
        IO.sync(println(s"$now will wait $delay for value $num"))
          .flatMap(_ => {
            IO.point(())
              .delay(delay milli)
              .map(_ => println(s"$now waited $delay for value $num"))
          })
          .map(_ => TimedValue(Improving.now, _ => num)))
      )

  def appendTwoExactFutures = forAll(exactFuture, exactFuture) {
    (f1: Future[IO1, Int], f2: Future[IO1, Int]) =>
      rts.unsafeRun((f1 |+| f2).ftv).t.exact must beEqualTo(
        rts.unsafeRun((f2 |+| f1).ftv).t.exact
      )
  }

  def appendTwoSlowFutures = forAll(slowFuture, slowFuture) {
    (df1: (Int, Future[IO1, Int]), df2: (Int, Future[IO1, Int]) ) =>
      println(s"Running delayed futures ${df1._1} and ${df2._1}")
      val val1 = rts.unsafeRun((df1._2 |+| df2._2).ftv).get
      println(s"computed val1 for delayed futures ${df1._1} and ${df2._1}: $val1")
      val val2 = rts.unsafeRun((df2._2 |+| df1._2).ftv).get
      println(s"computed val2 for delayed futures ${df1._1} and ${df2._1}: $val2")
      val1 must beEqualTo(val2)
  }

  def appendExactToSlowFutures = forAll(exactFuture, slowFuture) {
    (f1:  Future[IO1, Int], df2: (Int, Future[IO1, Int]) ) =>
      println(s"Running exact $f1 and delayed ${df2._1}")

      val val1 = rts.unsafeRun((f1 |+| df2._2).ftv).get
      println(s"computed val1 for exact $f1 and delayed ${df2._1}: $val1")
      val val2 = rts.unsafeRun((df2._2 |+| f1).ftv).get
      println(s"computes val2 for exact $f1 and delayed ${df2._1}: $val2")
      val1 must beEqualTo(val2)
  }

}
