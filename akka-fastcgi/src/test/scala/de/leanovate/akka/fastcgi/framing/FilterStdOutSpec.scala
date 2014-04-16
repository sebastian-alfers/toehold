/*    _             _           _     _                            *\
**   | |_ ___   ___| |__   ___ | | __| |   License: MIT  (2014)    **
**   | __/ _ \ / _ \ '_ \ / _ \| |/ _` |                           **
**   | || (_) |  __/ | | | (_) | | (_| |                           **
\*    \__\___/ \___|_| |_|\___/|_|\__,_|                           */

package de.leanovate.akka.fastcgi.framing

import org.specs2.mutable.Specification
import de.leanovate.akka.testutil.CollectingPMStream
import akka.util.ByteString
import de.leanovate.akka.fastcgi.records.{FCGIEndRequest, FCGIStdErr, FCGIStdOut}
import de.leanovate.akka.tcp.PMStream.{EOF, NoControl, Data}
import org.specs2.matcher.ShouldMatchers

class FilterStdOutSpec extends Specification with ShouldMatchers {
  "FilterStdOut" should {
    "only only pass FCGISTdOut records" in {
      val stderrs = Seq.newBuilder[ByteString]
      val out = new CollectingPMStream[ByteString]
      val pipe = Framing.filterStdOut(stderr => stderrs += stderr) |> out

      pipe.push(FCGIStdOut(1, ByteString("Hello")), FCGIStdErr(1, ByteString("something")),
                 FCGIStdOut(1, ByteString("World")), FCGIEndRequest(1))

      out.eof should beTrue
      stderrs.result() shouldEqual Seq(ByteString("something"))
      out.result() shouldEqual Seq(ByteString("Hello"), ByteString("World"))
    }

    "honour eof" in {
      val stderrs = Seq.newBuilder[ByteString]
      val out = new CollectingPMStream[ByteString]
      val pipe = Framing.filterStdOut(stderr => stderrs += stderr) |> out

      pipe.send(Data(FCGIStdOut(1, ByteString("Hello"))), NoControl)
      pipe.send(Data(FCGIStdErr(1, ByteString("something"))), NoControl)
      pipe.send(EOF, NoControl)
      pipe.send(Data(FCGIStdOut(1, ByteString("World"))), NoControl)
      pipe.send(EOF, NoControl)

      out.eof should beTrue
      stderrs.result() shouldEqual Seq(ByteString("something"))
      out.result() shouldEqual Seq(ByteString("Hello"))
    }

    "eof on FCGIEndRecord" in {
      val stderrs = Seq.newBuilder[ByteString]
      val out = new CollectingPMStream[ByteString]
      val pipe = Framing.filterStdOut(stderr => stderrs += stderr) |> out

      pipe.push(FCGIStdOut(1, ByteString("Hello")), FCGIStdErr(1, ByteString("something")),
                 FCGIEndRequest(1), FCGIStdOut(1, ByteString("World")), FCGIEndRequest(1))

      out.eof should beTrue
      stderrs.result() shouldEqual Seq(ByteString("something"))
      out.result() shouldEqual Seq(ByteString("Hello"))
    }
  }
}
