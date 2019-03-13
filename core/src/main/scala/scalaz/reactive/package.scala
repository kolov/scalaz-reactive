package scalaz
import scalaz.zio.ZIO
import scalaz.zio.clock.Clock

package object reactive extends EventSyntax {

  type RE      = Any with Clock
  type RIO[+A] = ZIO[Any with Clock, Nothing, A]

}
