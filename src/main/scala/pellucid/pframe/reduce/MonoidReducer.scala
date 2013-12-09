package pellucid.pframe
package reduce

import scala.annotation.tailrec

import spire.algebra.Monoid
import spire.syntax.monoid._

private[reduce] final class MonoidReducer[A: Monoid] extends Reducer[A, A] {
  def reduce(column: Column[A], indices: Array[Int], start: Int, end: Int): A = {
    @tailrec def loop(i: Int, acc: A): A = if (i < end) {
      val row = indices(i)
      if (column.exists(row)) {
        loop(i + 1, acc |+| column.value(row))
      } else {
        loop(i + 1, acc)
      }
    } else acc

    loop(start, Monoid[A].id)
  }
}