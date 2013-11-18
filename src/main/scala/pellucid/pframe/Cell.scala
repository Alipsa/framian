package pellucid
package pframe

sealed trait Cell[+A] {
  def isMissing: Boolean
  def value: Option[A]

  def fold[B](na: => B, nm: => B)(f: A => B): B = this match {
    case NA => na
    case NM => nm
    case Value(a) => f(a)
  }

  def map[B](f: A => B): Cell[B] = this match {
    case Value(a) => Value(f(a))
    case NA => NA
    case NM => NM
  }

  def flatMap[B](f: A => Cell[B]): Cell[B] = this match {
    case Value(a) => f(a)
    case NA => NA
    case NM => NM
  }

  def filter(f: A => Boolean): Cell[A] = this match {
    case Value(a) if !f(a) => NA
    case other => other
  }
}

sealed trait Missing extends Cell[Nothing] {
  def isMissing = true
  def value = None
}

/**
 * Value is Not Available (NA). This indicates the value is simply missing.
 */
case object NA extends Missing

/**
 * Value is Not Meaningful (NM). This indicates that a values exist, but it is
 * not meaningful. For instance, if we divide by 0, then the value is not
 * meaningful, but we wouldn't necessarily say it is missing.
 */
case object NM extends Missing

case class Value[+A](get: A) extends Cell[A] {
  def value = Some(get)
  def isMissing = false
}
