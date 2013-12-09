package pellucid.pframe

import language.experimental.macros
import scala.reflect.macros.Context

import scala.collection.immutable.BitSet
import scala.{ specialized => spec }
import scala.annotation.{ unspecialized => unspec }

import spire.algebra._

/**
 * A `Column` represents an `Int`-indexed set of values. The defining
 * characteristic is that a column is always defined for all `Int` values. In
 * order to interact with a column in an optimal and concrete way, we need an
 * external notion of valid rows to restrict the input by. A [[Series]], for
 * instance, ties a `Column` together with an [[Index]] to restrict the set of
 * rows being used.
 */
trait Column[@spec(Int,Long,Float,Double) +A] extends ColumnLike[Column[A]] {

  /**
   * Returns `true` if the value exists; that is, it is available and
   * meaningful. This method must be called before any calls to `missing` or
   * `value`, since it is essentially the switch that let's you know which
   * one to call.
   */
  def exists(row: Int): Boolean

  /**
   * If `exists(row) == false`, then this will return either `NA` or `NM` to
   * indicate the value is not available or not meaningful (resp.). If
   * `exists(row) == true`, then the result of calling this is undefined.
   */
  def missing(row: Int): Missing

  /**
   * If `exists(row) == true`, then this will return the value stored in row
   * `row`. If `exists(row) == false`, then the result of calling this is
   * undefined.
   */
  def value(row: Int): A

  def foldRow[B](row: Int)(f: A => B, g: Missing => B): B =
    if (exists(row)) f(value(row)) else g(missing(row))

  def apply(row: Int): Cell[A] =
    foldRow(row)(Value(_), m => m)

  /**
   * Map all existing values to a given value.
   */
  def map[B](f: A => B): Column[B] = new MappedColumn(f, this)

  /**
   * Filter a column by a given predicate. All values that have been filtered
   * out are turned into `NA` (Not Available).
   */
  def filter(f: A => Boolean): Column[A] = new FilteredColumn(f, this)

  /**
   * Masks this column with a given `BitSet`. That is, a value only exists at
   * a row if it exists in both the source `Column` and if `bitset(row)` is
   * `true`. If a value exists in the source `Column`, but `bitset(row)` is
   * `false`, then that value is treated as `NA` (Not Available).
   */
  def mask(bits: Int => Boolean): Column[A] = new MaskedColumn(bits, this)

  /**
   * Shift all rows in this column down by `rows`. If `rows` is negative, then
   * they will be shifted up by `-rows`.
   */
  def shift(rows: Int): Column[A] = new ShiftColumn(rows, this)

  /**
   * Returns a Column whose rows are mapped with the given `index` to rows in
   * this column. If a row doesn't exist in the index (ie. is less than 0 or
   * greater than or equal to `index.length`), then `NA` is returned.
   */
  def reindex(index: Array[Int]): Column[A] = new ReindexColumn(index, this)

  /**
   * Force a specific row to be not available (`NA`).
   */
  def setNA(row: Int): Column[A] = new SetNAColumn(row, this)

  /**
   * This method should be used to return a column specialized on a particular
   * index. That means it can drop all values that aren't being accessed by
   * the index and remove some of the indirection built-up via things like
   * `map`, `filter`, etc.
   */
  def optimize(index: Index[_]): Column[A] = ???

  final def cells(rng: Range): Vector[Cell[A]] = rng.map(this(_))(collection.breakOut)

  override def toString: String =
    ((0 until Column.ToStringLength).map(apply(_)).map(_.toString) :+ "...").mkString("Column(", ", ", ")")
}

object Column extends ColumnAlgebras {
  private val ToStringLength = 5

  def empty[A]: Column[A] = new EmptyColumn[A]

  def const[@spec(Int,Long,Float,Double) A](value: A): Column[A] = new ConstColumn(value)

  def apply[A](f: Int => A): Column[A] = new InfiniteColumn(f)

  def fromCells[A](cells: IndexedSeq[Cell[A]]): Column[A] = new CellColumn(cells)

  def fromArray[A](values: Array[A]): Column[A] = new DenseColumn(BitSet.empty, BitSet.empty, values)

  def fromMap[A](values: Map[Int, A]): Column[A] = new MapColumn(values)

  def wrap[A](f: Int => Cell[A]): Column[A] = new WrappedColumn(f)

  implicit def monoid[A] = new Monoid[Column[A]] {
    def id: Column[A] = empty[A]
    def op(lhs: Column[A], rhs: Column[A]): Column[A] = new MergedColumn(lhs, rhs)
  }

  // implicit def columnOps[A](lhs: Column[A]) = new ColumnOps[A](lhs)
}

// // This class is required to get around some spec/macro bugs.
// final class ColumnOps[A](lhs: Column[A]) {
//   def map0[B](f: A => B): Column[B] = macro ColumnOps.mapImpl[A, B]
// }

// object ColumnOps {
//   def mapImpl[A, B: c.WeakTypeTag](c: Context)(f: c.Expr[A => B]): c.Expr[Column[B]] = {
//     import c.universe._
//     val lhs = c.prefix.tree match {
//       case Apply(TypeApply(_, _), List(lhs)) => lhs
//       case t => c.abort(c.enclosingPosition,
//         "Cannot extract subject of op (tree = %s)" format t)
//     }
// 
//     c.Expr[Column[B]](c.resetLocalAttrs(q"""{
//       new Column[${weakTypeTag[B]}] {
//         val col = ${lhs}
//         def exists(row: Int): Boolean = col.exists(row)
//         def missing(row: Int): Missing = col.missing(row)
//         def value(row: Int): ${weakTypeTag[B]} = $f.apply(col.value(row))
//       }
//     }"""))
//   }
// }
