package framian.csv

import java.nio.charset.{ Charset, StandardCharsets }
import java.io.File
import java.io.{ InputStream, FileInputStream }
import java.io.{ Reader, InputStreamReader }

case class CsvParser(format: CsvFormat) {
  import ParserState._
  import Instr._

  def mkError(input: Input, s0: ParserState, s1: ParserState, row: Long, msg: String, pos: Long): CsvError = {
    val context = input.substring(s0.input.mark, s1.input.mark)
    CsvError(msg, pos, context, row, pos - s0.input.mark + 1)
  }

  def parseResource[A](a: A, close: A => Unit)(read: A => Option[String]): Csv = {
    def loop(s0: ParserState, row: Long, acc: Vector[Either[CsvError, CsvRow]]): Csv = {
      val (s1, instr) = parse(s0)

      instr match {
        case Emit(cells) =>
          loop(s1, row + 1, acc :+ Right(cells))
        case Fail(msg, pos) =>
          loop(s1, row + 1, acc :+ Left(mkError(s1.input, s0, s1, row, msg, pos)))
        case Resume =>
          loop(s1, row, acc)
        case NeedInput =>
          read(a) match {
            case Some(chunk) =>
              loop(s1.mapInput(_.append(chunk)), row, acc)
            case None =>
              loop(s1.mapInput(_.finished), row, acc)
          }
        case Done =>
          val (hdr, rows) = if (format.header) {
            acc.headOption match {
              case Some(Right(row)) => (Some(row.text(format)), acc.tail)
              case _ => (None, acc)
            }
          } else (None, acc)
          Csv(format, hdr, rows)
      }
    }

    try {
      read(a).map { input0 =>
        loop(ParseRow(Input.init(input0)), 1L, Vector.empty)
      }.getOrElse {
        Csv.empty(format)
      }
    } finally {
      try {
        close(a)
      } catch { case (_: Exception) =>
        // Do nothing - hopefully letting original exception through.
      }
    }
  }

  def parseReader(reader: Reader): Csv = {
    val buffer = new Array[Char](Csv.BufferSize)
    parseResource[Reader](reader, _.close()) { reader =>
      val len = reader.read(buffer)
      if (len >= 0) {
        Some(new String(buffer, 0, len))
      } else {
        None
      }
    }
  }

  def parseInputStream(is: InputStream, charset: Charset = StandardCharsets.UTF_8): Csv =
    parseReader(new InputStreamReader(is, charset))

  def parseFile(file: File, charset: Charset = StandardCharsets.UTF_8): Csv =
    parseInputStream(new FileInputStream(file), charset)

  def parseString(input: String): Csv = {
    var next: Option[String] = Some(input)
    parseResource[Unit]((), _ => ()) { _ =>
      val chunk = next; next = None; chunk
    }
  }

  private def parse(state: ParserState): (ParserState, Instr[CsvRow]) = {
    import format._

    val input: Input = state.input
    var pos: Long = input.mark
    def ch: Char = input.charAt(pos)
    def endOfInput: Boolean = pos >= input.length
    def endOfFile: Boolean = endOfInput && input.isLast
    def advance(i: Long = 1): Unit = pos += i
    def retreat(i: Long = 1): Unit = pos -= i

    def isFlag(str: String): () => Int = {
      def loop(i: Int): Int =
        if (i >= str.length) {
          retreat(i)
          i
        } else if (endOfInput) {
          retreat(i)
          if (endOfFile) 0 else -1
        } else if (str.charAt(i) == ch) {
          advance()
          loop(i + 1)
        } else {
          retreat(i)
          0
        }

      () => loop(0)
    }

    def either(f0: () => Int, f1: () => Int): () => Int = { () =>
      val i = f0()
      if (i == 0) f1() else i
    }

    val isQuote = isFlag(quote)
    val isQuoteEscape = isFlag(quoteEscape)
    val isSeparator = isFlag(separator)
    val isRowDelim = rowDelim.alternate.map { alt =>
      either(isFlag(rowDelim.value), isFlag(alt))
    }.getOrElse(isFlag(rowDelim.value))
    val isEndOfCell = either(isSeparator, isRowDelim)
    def isEscapedQuote() = {
      val e = isQuoteEscape()
      if (e > 0) {
        advance(e)
        val q = isQuote()
        retreat(e)
        if (q > 0) q + e
        else q
      } else {
        e
      }
    }

    def unquotedCell(): ParseResult[CsvCell] = {
      val start = pos
      def loop(): ParseResult[CsvCell] = {
        val flag = isEndOfCell()
        if (flag > 0 || endOfFile) {
          val value = input.substring(start, pos)
          val csvCell =
            if (value == empty) CsvCell.Empty
            else if (value == invalid) CsvCell.Invalid
            else CsvCell.Data(value)
          Emit(csvCell)
        } else if (flag == 0) {
          advance()
          loop()
        } else {
          NeedInput
        }
      }

      loop()
    }

    def quotedCell(): ParseResult[CsvCell] = {
      val start = pos
      def loop(): ParseResult[CsvCell] = {
        if (endOfInput) {
          if (endOfFile) {
            Fail("Unmatched quoted string at end of file", pos)
          } else {
            NeedInput
          }
        } else {
          val d = isRowDelim()
          val e = isEscapedQuote()
          val q = isQuote()

          if (d < 0 || e < 0 || q < 0) {
            NeedInput
          } else if (d > 0) {
            Fail("Unmatched quoted string at row delimiter", pos)
          } else if (e > 0) {
            advance(e)
            loop()
          } else if (q > 0) {
            val escaped = input.substring(start, pos).replace(escapedQuote, quote)
            advance(q)
            Emit(CsvCell.Data(escaped))
          } else {
            advance(1)
            loop()
          }
        }
      }

      loop()
    }

    def cell(): ParseResult[CsvCell] = {
      val q = isQuote()
      if (q == 0) {
        unquotedCell()
      } else if (q > 0) {
        advance(q)
        quotedCell()
      } else {
        NeedInput
      }
    }

    def skipToNextRow(): Boolean = {
      val d = isRowDelim()
      if (d == 0) {
        advance(1)
        skipToNextRow()
      } else if (d > 0) {
        advance(d)
        true
      } else {
        if (input.isLast)
          advance(input.length - pos)
        input.isLast
      }
    }

    def row(cells: Vector[CsvCell]): (ParserState, Instr[CsvRow]) = {
      val start = pos
      def needInput() = (ContinueRow(cells, input.marked(start)), NeedInput)

      val s = isSeparator()
      if (s == 0) {
        val r = isRowDelim()
        if (r > 0 || endOfFile) {
          advance(r)
          (ParseRow(input.marked(pos)), Emit(new CsvRow(cells)))
        } else if (r == 0) {
          (SkipRow(input.marked(pos)), Fail("Expected separator, row delimiter, or end of file", pos))
        } else {
          needInput()
        }
      } else if (s > 0) {
        advance(s)
        cell() match {
          case Emit(c) =>
            row(cells :+ c)
          case f @ Fail(_, _) =>
            (SkipRow(input.marked(pos)), f)
          case NeedInput =>
            needInput()
        }
      } else {
        needInput()
      }
    }

    state match {
      case ContinueRow(partial, _) =>
        row(partial)

      case instr @ ParseRow(_) =>
        if (endOfFile) {
          (instr, Done)
        } else {
          cell() match {
            case Emit(csvCell) =>
              row(Vector(csvCell))
            case f @ Fail(_, _) =>
              (SkipRow(input.marked(pos)), f)
            case NeedInput =>
              (instr, NeedInput)
          }
        }

      case SkipRow(_) =>
        if (skipToNextRow()) {
          (ParseRow(input.marked(pos)), Resume)
        } else {
          (SkipRow(input.marked(pos)), NeedInput)
        }
    }
  }
}
