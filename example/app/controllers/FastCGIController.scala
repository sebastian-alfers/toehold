package controllers

import play.api.mvc._
import play.api.Play._
import akka.util.{ByteString, Timeout}
import play.api.libs.concurrent.Akka
import play.api.libs.iteratee.Iteratee
import scala.concurrent.duration._
import akka.pattern.ask
import play.api.libs.concurrent.Execution.Implicits._
import de.leanovate.akka.fastcgi.FCGIRequestActor
import de.leanovate.akka.iteratee.adapt.PromiseEnumerator
import de.leanovate.akka.fastcgi.request.{FCGIResponderError, FCGIResponderSuccess, FCGIRequestContent, FCGIResponderRequest}

object FastCGIController extends Controller {
  val fastCGIHost = configuration.getString("fastcgi.host").getOrElse("localhost")

  val fastCGIPort = configuration.getInt("fastcgi.port").getOrElse(9001)

  implicit val fastGGITimeout = Timeout(configuration.getMilliseconds("fastcgi.timeout").map(_.milliseconds)
    .getOrElse(60.seconds))

  val fcgiRequestActor = Akka.system.actorOf(FCGIRequestActor.props(fastCGIHost, fastCGIPort))

  def serve(documentRoot: String, path: String) = EssentialAction {
    requestHeader =>
      requestHeader.contentType.map {
        contentType =>
          requestHeader.headers.get("content-length").map {
            contentLength =>
              val content = new PromiseEnumerator[Array[Byte]]
              val request = FCGIResponderRequest(
                requestHeader.method,
                "/" + path,
                requestHeader.rawQueryString,
                documentRoot,
                requestHeader.headers.toMap,
                Some(FCGIRequestContent(contentType, contentLength.toLong,
                  content.map(bytes => ByteString(bytes))))
              )

              (fcgiRequestActor ? request).foreach {
                case FCGIResponderSuccess(headers, content) =>
                  println(headers)
                  content |>> Iteratee.foreach[ByteString] {
                    chunk =>
                      println("Chunk: " + chunk.utf8String)
                  }.map {
                    _ =>
                      println("EOF")
                  }
                case FCGIResponderError(msg) =>
                  InternalServerError(msg)
              }

              content.promisedIteratee.map(_ => Ok("bla"))
          }.getOrElse {
            Iteratee.ignore[Array[Byte]].map {
              _ => new Status(LENGTH_REQUIRED)
            }
          }
      }.getOrElse {
        val request = FCGIResponderRequest(
          requestHeader.method,
          "/" + path,
          requestHeader.rawQueryString,
          documentRoot,
          requestHeader.headers.toMap,
          None
        )
        println(request)

        Iteratee.ignore[Array[Byte]].mapM(
          _ =>
            (fcgiRequestActor ? request).map {
              case FCGIResponderSuccess(headers, content) =>
                println(headers)
                Status(OK).chunked(content.map(_.toArray))
              case FCGIResponderError(msg) =>
                InternalServerError(msg)
            }
        )
      }
  }
}
