/*    _             _           _     _                            *\
**   | |_ ___   ___| |__   ___ | | __| |   License: MIT  (2014)    **
**   | __/ _ \ / _ \ '_ \ / _ \| |/ _` |                           **
**   | || (_) |  __/ | | | (_) | | (_| |                           **
\*    \__\___/ \___|_| |_|\___/|_|\__,_|                           */

package de.leanovate.akka.fastcgi.records

import scala.concurrent.ExecutionContext
import play.api.libs.iteratee._
import akka.util.ByteString

object Framing {
  def toFCGIStdin(id: Int)(implicit ctx: ExecutionContext): Enumeratee[ByteString, FCGIRecord] =
    Enumeratee.mapInput[ByteString] {
      case Input.El(content) =>
        Input.El(FCGIStdin(id, content))
      case Input.Empty =>
        Input.Empty
      case Input.EOF =>
        println(">>> the eof")
        Input.El(FCGIStdin(id, ByteString.empty))
    }

  def filterStdOut(stderr: ByteString => Unit)(implicit ctx: ExecutionContext) = Enumeratee.mapConcatInput[FCGIRecord] {
    case FCGIStdOut(_, content) =>
      Seq(Input.El(content))
    case FCGIStdErr(_, content) =>
      stderr(content)
      Seq.empty
    case _: FCGIEndRequest =>
      Seq(Input.EOF)
  }

  def headerLinesFromStdOut(implicit ctx: ExecutionContext) = {

    var buffer = ByteString.empty
    val lines = Seq.newBuilder[(String, String)]
    def step(i: Input[ByteString]): Iteratee[ByteString, Seq[(String, String)]] = i match {

      case Input.EOF =>
        if (buffer.isEmpty) {
          Done(lines.result(), Input.EOF)
        } else {
          Done(lines.result(), Input.El(buffer))
        }
      case Input.Empty => Cont(step)
      case Input.El(e) =>
        buffer ++= e
        var idx = buffer.indexOf('\n')
        var end = false

        while (!end && idx >= 0) {
          val line = if (idx > 0 && buffer(idx - 1) == '\r') {
            buffer.take(idx - 1)
          } else {
            buffer.take(idx)
          }
          if (line.isEmpty) {
            end = true
          } else {
            val delimIdx = line.indexOf(':')
            if (delimIdx >= 0) {
              lines += line.take(delimIdx).utf8String -> line.drop(delimIdx + 1).utf8String.trim
            }
          }
          buffer = buffer.drop(idx + 1)
          idx = buffer.indexOf('\n')
        }
        if (end) {
          Done(lines.result(), Input.El(buffer))
        } else {
          Cont(step)
        }
    }
    Cont(step)
  }
}