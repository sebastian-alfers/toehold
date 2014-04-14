/*    _             _           _     _                            *\
**   | |_ ___   ___| |__   ___ | | __| |   License: MIT  (2014)    **
**   | __/ _ \ / _ \ '_ \ / _ \| |/ _` |                           **
**   | || (_) |  __/ | | | (_) | | (_| |                           **
\*    \__\___/ \___|_| |_|\___/|_|\__,_|                           */

package de.leanovate.akka.fastcgi.framing

import org.scalatest.{Matchers, FunSpec}
import akka.util.ByteString
import org.apache.commons.codec.binary.Hex
import de.leanovate.akka.fastcgi.records.{FCGIEndRequest, FCGIStdOut, FCGIRecord}
import de.leanovate.akka.testutil.CollectingPMStream
import scala.util.Random

class BytesToFCGIRecordSpec extends FunSpec with Matchers {
  describe("BytesToFCGIRecords") {
    val responseData = ByteString(Hex
      .decodeHex(
        "0106000100860200582d506f77657265642d42793a205048502f352e342e32340d0a436f6e74656e742d747970653a20746578742f68746d6c0d0a0d0a4c65747320726574726965766520616c6c20746865207661726961626c6573207375626d697474656420746f2074686973200a7363726970742076696120612047455420726571756573743a3c62723e0a000001060001001f010073637269707420766961206120504f535420726571756573743a3c62723e0a0001060001014d0300416c6c20454e5620726571756573743a3c62723e0a555345523d6a756e676c61733c62723e0a484f4d453d2f55736572732f6a756e676c61733c62723e0a464347495f524f4c453d415554484f52495a45523c62723e0a5343524950545f46494c454e414d453d2e2f6170702d7068702f746573742e7068703c62723e0a51554552595f535452494e473d3c62723e0a524551554553545f4d4554484f443d4745543c62723e0a5343524950545f4e414d453d2f746573742e7068703c62723e0a524551554553545f5552493d2f746573742e7068703c62723e0a444f43554d454e545f5552493d2f746573742e7068703c62723e0a444f43554d454e545f524f4f543d2e2f6170702d7068703c62723e0a5345525645525f50524f544f434f4c3d485454502f312e313c62723e0a474154455741595f494e544552464143453d4347492f312e313c62723e0a0000000106000101c50300416c6c205f53455256455220726571756573743a3c62723e0a555345523d6a756e676c61733c62723e0a484f4d453d2f55736572732f6a756e676c61733c62723e0a464347495f524f4c453d415554484f52495a45523c62723e0a5343524950545f46494c454e414d453d2e2f6170702d7068702f746573742e7068703c62723e0a51554552595f535452494e473d3c62723e0a524551554553545f4d4554484f443d4745543c62723e0a5343524950545f4e414d453d2f746573742e7068703c62723e0a524551554553545f5552493d2f746573742e7068703c62723e0a444f43554d454e545f5552493d2f746573742e7068703c62723e0a444f43554d454e545f524f4f543d2e2f6170702d7068703c62723e0a5345525645525f50524f544f434f4c3d485454502f312e313c62723e0a474154455741595f494e544552464143453d4347492f312e313c62723e0a5048505f53454c463d2f746573742e7068703c62723e0a524551554553545f54494d455f464c4f41543d313339363635313037342e333031343c62723e0a524551554553545f54494d453d313339363635313037343c62723e0a617267763d41727261793c62723e0a617267633d303c62723e0a00000001030001000800000000000000534552"
          .toCharArray))

    def isExpected(result: Seq[FCGIRecord]) = {

      result should have size 5
      result(1) should be(FCGIStdOut(1, ByteString(Hex
        .decodeHex("73637269707420766961206120504f535420726571756573743a3c62723e0a".toCharArray))))
      result(2) should be(FCGIStdOut(1, ByteString(Hex
        .decodeHex(
          "416c6c20454e5620726571756573743a3c62723e0a555345523d6a756e676c61733c62723e0a484f4d453d2f55736572732f6a756e676c61733c62723e0a464347495f524f4c453d415554484f52495a45523c62723e0a5343524950545f46494c454e414d453d2e2f6170702d7068702f746573742e7068703c62723e0a51554552595f535452494e473d3c62723e0a524551554553545f4d4554484f443d4745543c62723e0a5343524950545f4e414d453d2f746573742e7068703c62723e0a524551554553545f5552493d2f746573742e7068703c62723e0a444f43554d454e545f5552493d2f746573742e7068703c62723e0a444f43554d454e545f524f4f543d2e2f6170702d7068703c62723e0a5345525645525f50524f544f434f4c3d485454502f312e313c62723e0a474154455741595f494e544552464143453d4347492f312e313c62723e0a"
            .toCharArray))))
      result(3) should be(FCGIStdOut(1, ByteString(Hex
        .decodeHex(
          "416c6c205f53455256455220726571756573743a3c62723e0a555345523d6a756e676c61733c62723e0a484f4d453d2f55736572732f6a756e676c61733c62723e0a464347495f524f4c453d415554484f52495a45523c62723e0a5343524950545f46494c454e414d453d2e2f6170702d7068702f746573742e7068703c62723e0a51554552595f535452494e473d3c62723e0a524551554553545f4d4554484f443d4745543c62723e0a5343524950545f4e414d453d2f746573742e7068703c62723e0a524551554553545f5552493d2f746573742e7068703c62723e0a444f43554d454e545f5552493d2f746573742e7068703c62723e0a444f43554d454e545f524f4f543d2e2f6170702d7068703c62723e0a5345525645525f50524f544f434f4c3d485454502f312e313c62723e0a474154455741595f494e544552464143453d4347492f312e313c62723e0a5048505f53454c463d2f746573742e7068703c62723e0a524551554553545f54494d455f464c4f41543d313339363635313037342e333031343c62723e0a524551554553545f54494d453d313339363635313037343c62723e0a617267763d41727261793c62723e0a617267633d303c62723e0a"
            .toCharArray))))
      result(4) should be(FCGIEndRequest(1))
    }

    it("should be able to convert a FCGI stream to a sequence of FCGIRecords") {
      val out = new CollectingPMStream[FCGIRecord]
      val pipe = Framing.bytesToFCGIRecords |> out

      pipe.push(responseData)

      isExpected(out.result())
    }

    it("should not break if the data is send in arbitrary chunks") {
      val out = new CollectingPMStream[FCGIRecord]
      val pipe = Framing.bytesToFCGIRecords |> out

      var idx = 0
      val chunks = Seq.newBuilder[ByteString]
      while (idx < responseData.length) {
        val end = Math.min(Random.nextInt(30) + 10 + idx, responseData.length)
        chunks += responseData.slice(idx, end)
        idx = end
      }

      pipe.push(chunks.result(): _ *)

      isExpected(out.result())
    }
  }
}