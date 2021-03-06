/*    _             _           _     _                            *\
**   | |_ ___   ___| |__   ___ | | __| |   License: MIT  (2014)    **
**   | __/ _ \ / _ \ '_ \ / _ \| |/ _` |                           **
**   | || (_) |  __/ | | | (_) | | (_| |                           **
\*    \__\___/ \___|_| |_|\___/|_|\__,_|                           */

package de.leanovate.akka.tcp

import de.leanovate.akka.testutil.CollectingPMSubscriber
import de.leanovate.akka.tcp.PMSubscriber.{Data, Subscription}
import org.specs2.mutable.Specification
import org.specs2.matcher.ShouldMatchers
import org.specs2.mock.Mockito

class AttachablePMSubscriberSpec extends Specification with ShouldMatchers with Mockito {
  "AttachablePMStream" should {
    "buffer data in unattached state" in {
      val attachable = new AttachablePMSubscriber[String]
      val subscription = mock[Subscription]

      attachable.onSubscribe(subscription)
      attachable.onNext(Data("1"))
      attachable.onNext(Data("2"))
      attachable.onNext(Data("3"))
      there was noCallsTo(subscription)

      val out = new CollectingPMSubscriber[String]

      attachable.subscribe(out)
      there was one(subscription).requestMore()
      out.markResume()

      there was two(subscription).requestMore()

      out.eof should beFalse
      out.result() shouldEqual Seq("1", "2", "3")
    }

    "should pass through after attached" in {
      val attachable = new AttachablePMSubscriber[String]
      val subscription = mock[Subscription]
      val out = new CollectingPMSubscriber[String]

      attachable.subscribe(out)

      attachable.onSubscribe(subscription)
      there was one(subscription).requestMore()
      attachable.onNext(Data("1"))
      attachable.onNext(Data("2"))
      attachable.onNext(Data("3"))
      out.markResume()

      there was two(subscription).requestMore()

      out.eof should beFalse
      out.result() shouldEqual Seq("1", "2", "3")
    }
  }
}
