package pellucid.pframe
package reduce

import spire.syntax.cfor._

final class Unique[A] extends Reducer[A, Set[A]] {

  final def reduce(column: Column[A], indices: Array[Int], start: Int, end: Int): Cell[Set[A]] = {
    val bldr = Set.newBuilder[A]
    cfor(start)(_ < end, _ + 1) { i =>
      val row = indices(i)
      if (column.isValueAt(row)) {
        bldr += column.valueAt(row)
      } else if (column.nonValueAt(row) == NM) {
        return NM
      }
    }
    Value(bldr.result())
  }
}
