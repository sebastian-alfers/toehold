/*    _             _           _     _                            *\
**   | |_ ___   ___| |__   ___ | | __| |   License: MIT  (2014)    **
**   | __/ _ \ / _ \ '_ \ / _ \| |/ _` |                           **
**   | || (_) |  __/ | | | (_) | | (_| |                           **
\*    \__\___/ \___|_| |_|\___/|_|\__,_|                           */

package de.leanovate.akka.tcp

import java.net.InetSocketAddress
import akka.actor.{Actor, ActorContext, Cancellable, ActorRef}
import de.leanovate.akka.tcp.PMSubscriber._
import akka.io.Tcp
import akka.util.ByteString
import de.leanovate.akka.tcp.PMSubscriber.Data
import scala.Some
import akka.event.LoggingAdapter
import scala.concurrent.stm.Ref
import scala.concurrent.duration._
import scala.language.postfixOps
import akka.io.Tcp.{Register, ConnectionClosed, Received}

/**
 * Common code shared by tcp client and server actors that are in a connected state.
 *
 * The main difference between client and server is how the connection is established and what should happen on
 * a disconnect.
 */
class TcpConnectedState(connection: ActorRef,
                        remoteAddress: InetSocketAddress,
                        localAddress: InetSocketAddress,
                        inStream: PMSubscriber[ByteString],
                        onClosing: () => Unit,
                        onDisconnect: () => Unit,
                        closeOnEof: Boolean,
                        inactivityTimeout: FiniteDuration,
                        suspendTimeout: FiniteDuration,
                        log: LoggingAdapter)(implicit self: ActorRef, context: ActorContext) {

  private val tickTime = 1.second

  private var tickGenerator: Option[Cancellable] = None

  private val inactivityDeadline = Ref[Deadline](Deadline.now + inactivityTimeout)

  private val readDeadline = Ref[Option[Deadline]](None)

  private val writeDeadline = Ref[Option[Deadline]](None)

  private val outPMSubscriber = new OutPMSubscriber

  inStream.onSubscribe(new ConnectionSubscription)

  connection ! Register(self)

  scheduleTick()

  def outStream: PMSubscriber[ByteString] = outPMSubscriber

  def receive: Actor.Receive = {

    case Received(data) =>
      if (log.isDebugEnabled) {
        log.debug(s"$localAddress -> $remoteAddress receive chunk: ${data.length} bytes")
      }
      inactivityDeadline.single.set(Deadline.now + inactivityTimeout)
      readDeadline.single.set(Some(Deadline.now + suspendTimeout))
      // Unluckily there is a lot of suspend/resume ping-pong, depending on the underlying buffers, sendChunk
      // might actually be called before the resume. This will become much cleaner with akka 2.3 in pull-mode
      connection ! Tcp.SuspendReading

      inStream.onNext(PMSubscriber.Data(data))

    case TcpConnectedSupport.WriteAck =>
      if (log.isDebugEnabled) {
        log.debug(s"$localAddress -> $remoteAddress inner write ack")
      }
      inactivityDeadline.single.set(Deadline.now + inactivityTimeout)
      writeDeadline.single.set(None)

      outPMSubscriber.acknowledge()

    case TickSupport.Tick =>
      if (readDeadline.single.get.exists(_.isOverdue())) {
        log.error(s"$localAddress -> $remoteAddress timed out in suspend reading for >= $suspendTimeout")
        connection ! Tcp.Abort
        onClosing()
      } else if (writeDeadline.single.get.exists(_.isOverdue())) {
        log.error(s"$localAddress -> $remoteAddress timed out in suspend write for >= $suspendTimeout")
        connection ! Tcp.Abort
        onClosing()
      } else if (inactivityDeadline.single.get.isOverdue()) {
        log.error(s"$localAddress -> $remoteAddress was inactive for >= $inactivityTimeout")
        connection ! Tcp.Abort
        onClosing()
      }
      scheduleTick()

    case c: ConnectionClosed =>
      if (log.isDebugEnabled) {
        log.debug(s"$localAddress -> $remoteAddress connection closed: $c")
      }
      inStream.onNext(PMSubscriber.EOF)
      tickGenerator.foreach(_.cancel())
      onDisconnect()
  }

  def close() {
    if (log.isDebugEnabled) {
      log.debug(s"$localAddress -> $remoteAddress is aborting")
    }
    connection ! Tcp.Abort
    onClosing()
  }

  private def scheduleTick() {

    tickGenerator.foreach(_.cancel())
    tickGenerator = Some(context.system.scheduler
      .scheduleOnce(tickTime, self, TickSupport.Tick)(context.dispatcher))
  }

  private class ConnectionSubscription extends Subscription {
    override def requestMore() {

      if (log.isDebugEnabled) {
        log.debug(s"$localAddress -> $remoteAddress resume reading")
      }
      inactivityDeadline.single.set(Deadline.now + inactivityTimeout)
      readDeadline.single.set(None)
      connection ! Tcp.ResumeReading
    }

    override def cancel(msg: String) {

      log.error(s"$localAddress -> $remoteAddress aborting connection: $msg")
      inactivityDeadline.single.set(Deadline.now + inactivityTimeout)
      readDeadline.single.set(None)
      connection ! Tcp.Abort
      onClosing()
    }
  }

  private class OutPMSubscriber extends PMSubscriber[ByteString] {
    private val writeBuffer = new WriteBuffer(remoteAddress, localAddress, log)

    private var subscription: Subscription = NoSubscription

    override def onSubscribe(_subscription: Subscription) {

      subscription = _subscription
    }

    override def onNext(chunk: Chunk[ByteString]) {

      writeBuffer.appendChunk(chunk) match {
        case None =>
          chunk match {
            case Data(data) =>
              if (log.isDebugEnabled) {
                log.debug(s"$localAddress -> $remoteAddress writing chunk ${data.length}")
              }
              inactivityDeadline.single.set(Deadline.now + inactivityTimeout)
              writeDeadline.single.set(Some(Deadline.now + suspendTimeout))
              connection ! Tcp.Write(data, TcpConnectedSupport.WriteAck)
            case EOF if closeOnEof =>
              if (log.isDebugEnabled) {
                log.debug(s"$localAddress -> $remoteAddress closing connection")
              }
              inactivityDeadline.single.set(Deadline.now + inactivityTimeout)
              writeDeadline.single.set(None)
              connection ! Tcp.Close
              onClosing()
            case EOF =>
          }
        case _ =>
      }
    }

    def acknowledge() {

      writeBuffer.takeChunk() match {
        case None =>
          log.error(s"$localAddress -> $remoteAddress write ack without pending")
        case Some(chunks) if chunks.isEmpty =>
          if (log.isDebugEnabled) {
            log.debug(s"$localAddress -> $remoteAddress resume out stream")
          }
          subscription.requestMore()
        case Some(chunks) =>
          chunks.head match {
            case Data(data) =>
              if (log.isDebugEnabled) {
                log.debug(s"$localAddress -> $remoteAddress writing chunk ${data.length}")
              }
              inactivityDeadline.single.set(Deadline.now + inactivityTimeout)
              writeDeadline.single.set(Some(Deadline.now + suspendTimeout))
              connection ! Tcp.Write(data, TcpConnectedSupport.WriteAck)
            case EOF if closeOnEof =>
              if (log.isDebugEnabled) {
                log.debug(s"$localAddress -> $remoteAddress closing connection")
              }
              inactivityDeadline.single.set(Deadline.now + inactivityTimeout)
              writeDeadline.single.set(None)
              onClosing()
              connection ! Tcp.Close
            case EOF =>
          }
      }
    }
  }
}
