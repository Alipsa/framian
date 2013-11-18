package pellucid.pframe

import scala.reflect.runtime.universe.{ TypeTag, typeTag }

import shapeless._
import shapeless.syntax.typeable._

/**
 * An abstraction for heterogeneously typed columns. We work with them by
 * casting to a real, typed column. Values that cannot be cast are treated as
 * `NM` (not meaningful) values.
 */
trait UntypedColumn {
  def cast[A: Typeable: TypeTag]: Column[A]

  def mask(bits: Int => Boolean): UntypedColumn
  def shift(rows: Int): UntypedColumn
}

case class TypedColumn[A](column: Column[A])(implicit val typeTagA: TypeTag[A]) extends UntypedColumn {
  def cast[B: Typeable: TypeTag]: Column[B] = {
    if (typeTagA.tpe <:< typeTag[B].tpe) {
      column.asInstanceOf[Column[B]]
    } else {
      new CastColumn[B](column)
    }
  }

  def mask(bits: Int => Boolean): UntypedColumn = TypedColumn(column.mask(bits))
  def shift(rows: Int): UntypedColumn = TypedColumn(column.shift(rows))
}
