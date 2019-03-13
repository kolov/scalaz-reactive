package scalaz.reactive
import scalaz.reactive.Event.Producer

case class Behaviour[A](funcs: Event[TimeFun[A]])

object Behaviour {

  def apply[A](head: TimeFun[A], tail: Event[TimeFun[A]]): RIO[Behaviour[A]] = {
    val producer: Producer[TimeFun[A]] = ???
    Event.fromProducer(producer).map(Behaviour(_))
  }
}
