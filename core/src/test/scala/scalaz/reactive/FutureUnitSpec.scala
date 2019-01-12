package scalaz.reactive

import java.text.SimpleDateFormat

import org.scalatest.FlatSpec
import org.specs2.matcher.MustMatchers
import scalaz.Scalaz._
import scalaz.reactive.io.types.IO1
import scalaz.zio.{IO, RTS}

import scala.concurrent.duration._

class FutureUnitSpec extends FlatSpec with FutureInstances with MustMatchers {

  import scalaz.reactive.io.instances._

  val rts = new RTS {}

  val DateFormat = "HH:mm:ss.SSS"
  def now = new SimpleDateFormat(DateFormat).format(System.currentTimeMillis())

  def appendTwoSlowFutures = {

    val delay1 = 100
    val value1 = 100
    val f1 = Future[IO1, Int](
      IO.sync(println(s"$now will wait $delay1 for value $value1"))
        .flatMap(_ => {
          IO.point(())
            .delay(delay1 milli)
            .map(_ => println(s"$now waited $delay1 for value $value1"))
        })
        .map(_ => TimedValue(Improving.now, _ => value1))
    )

    val delay2 = 500
    val value2 = 500
    val f2 = Future[IO1, Int](
      IO.sync(println(s"$now will wait $delay2 for value $value2"))
        .flatMap(_ => {
          IO.point(())
            .delay(delay2 milli)
            .map(_ => println(s"$now waited $delay2 for value $value2"))
        })
        .map(_ => TimedValue(Improving.now, _ => value2))
    )

    println(s"Running $f1 and $f2")
    val val1 = rts.unsafeRun((f1 |+| f2).ftv).get
    println(s"computed val1: $val1")
    val val2 = rts.unsafeRun((f2 |+| f1).ftv).get
    println(s"computed val2: $val2")
    val1 must beEqualTo(val2)
  }


}
