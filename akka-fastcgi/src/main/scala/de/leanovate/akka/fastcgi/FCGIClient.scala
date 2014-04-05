package de.leanovate.akka.fastcgi

import akka.actor.{ActorLogging, ActorRef, Props, Actor}
import java.net.InetSocketAddress
import akka.io.Tcp._
import akka.io.{Tcp, IO}
import de.leanovate.akka.fastcgi.records.{FilterStdOut, BytesToFCGIRecords, Framing, FCGIRecord}
import FCGIClient._
import de.leanovate.akka.iteratee.tcp.{FeedSink, InStreamEnumerator, OutStreamAdapter}
import akka.util.ByteString

class FCGIClient(remote: InetSocketAddress, handler: FCGIConnectionHandler) extends Actor with ActorLogging {

  import context.system
  import context.dispatcher

  IO(Tcp) ! Connect(remote)

  def receive = {

    case c@CommandFailed(_: Connect) =>
      if (log.isDebugEnabled) {
        log.debug(s"Connect failed: $c")
      }
      handler.connectionFailed()
    case c@Connected(remote, local) =>
      if (log.isDebugEnabled) {
        log.debug(s"Connected $local -> $remote")
      }
      sender ! Register(self)
      val out = new OutStreamAdapter[FCGIRecord](sender, FCGIRecord, SendRecordAck)
      val in = new InStreamEnumerator(sender)
      val httpExtractor = new HeaderExtractor({
        headers =>
          handler.headerReceived(headers, in)
      }, in)
      val filterStdOut = new FilterStdOut(stderrToLog, httpExtractor)
      val bytesToFCGIRecords = new BytesToFCGIRecords(filterStdOut)
      context become connected(sender, bytesToFCGIRecords, out)
      handler.connected(out.iterator)
  }

  def connected(connection: ActorRef, in: FeedSink[ByteString],
    out: OutStreamAdapter[FCGIRecord]): PartialFunction[Any, Unit] = {

    case Received(data) =>
      if (log.isDebugEnabled) {
        log.debug(s"Chunk: ${data.length} bytes")
      }
      in.feedChunk(data)
    case SendRecordAck =>
      if (log.isDebugEnabled) {
        log.debug("Write ack")
      }
      out.acknowledge()
    case c: ConnectionClosed =>
      if (log.isDebugEnabled) {
        log.debug(s"Connection closed: $c")
      }
      in.feedEOF()
      context stop self
  }

  private def stderrToLog(stderr: ByteString) {

    log.error(s"Stderr: ${stderr.utf8String}")
  }

}

object FCGIClient {
  def props(hostname: String, port: Int, handler: FCGIConnectionHandler) =
    Props(classOf[FCGIClient], new InetSocketAddress(hostname, port), handler)

  def props(remote: InetSocketAddress, handler: FCGIConnectionHandler) =
    Props(classOf[FCGIClient], remote, handler)

  case object SendRecordAck extends Event

}