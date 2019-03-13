package scalaz.reactive.examples

import scalaz.reactive._
import scalaz.zio.{App, IO, ZIO}

import scala.io.StdIn

/**
  * Example from https://wiki.haskell.org/FRP_explanation_using_reactive-banana
  */
object Synthesizer extends App {

  type Octave = Int

  case class Pitch(val sign: String)
  object PA extends Pitch("a")
  object PB extends Pitch("b")
  object PC extends Pitch("c")
  object PD extends Pitch("d")
  object PE extends Pitch("e")
  object PF extends Pitch("f")
  object PG extends Pitch("g")

  case class Note(octave: Octave, pitch: Pitch)

  val table =
    Map(
      'a' -> PA,
      'b' -> PB,
      'b' -> PB,
      'c' -> PC,
      'd' -> PD,
      'e' -> PE,
      'f' -> PF,
      'g' -> PG
    )

  def build(eKey: Event[Char]): RIO[Behaviour[Octave]] = {
        val ePitch: Event[Pitch] = Event.joinMaybes(
          eKey.map(table.get(_))
        )

    val eOctChange: Event[Octave => Octave] = Event.joinMaybes(
      eKey
        .map { k =>
          k match {
            case '+' => Some((x: Octave) => x + 1)
            case '-' => Some((x: Octave) => x - 1)
            case _   => None
          }
        }
    )

    val acc: RIO[Event[TimeFun.K[Octave]]] = Event.accumE(0)(eOctChange).map(_.map((x: Octave) => TimeFun.K(x)))
    val bOctave  = acc.flatMap( e => Behaviour(TimeFun.K(0), e))

    //    val bPitch: Behaviour[Pitch] = Behaviour(
    //      Reactive(TimeFun.K(PA), ePitch.map(p => TimeFun.K(p)))
    //    )

    //    val bNote = (bOctave |@| bPitch) { Note.apply _ }

      bOctave

  }

  def run(args: List[String]) =
    myAppLogic.fold(_ => 1, _ => 0)
  def eKey(): Event[Char] = {
    val nextChar: IO[Void, Char] = IO.sync {
      println(s"Waiting for input")
      StdIn.readChar()
    }

    Event(nextChar.flatMap { c =>
      Time.now.map((_, Reactive(c, eKey)))
    })
  }

  def myAppLogic: IO[Void, Unit] = {

    val toSink: Behaviour[Octave] = build(eKey())

    Sink.sinkB(
      toSink,
      (tn: TimeFun[Octave]) =>
        Time.now.map { t =>
          println(s"Octave is ${tn.apply(t)}")
        }
    )
  }

}