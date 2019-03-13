package scalaz.reactive

case class Behaviour[A](funcs: Event[TimeFun[A]])
