import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

import Hub.Message.ExternalMessage
import com.google.protobuf.ByteString

import scala.concurrent.ExecutionContext.Implicits.global
import org.java_websocket.client.WebSocketClient
import org.java_websocket.drafts.Draft_6455
import org.java_websocket.handshake.ServerHandshake
import io.protoless.generic.auto._
import io.protoless.syntax._
import ujson._

import scala.collection.mutable
import scala.concurrent.{Future, Promise}
import scala.util.Try

package object Hub {
    type JsonObject=ujson.Js.Obj
    type QueryObject = Map[String, Vector[String]]

    def QueryToJson(query: Map[String, Vector[String]]): JsonObject = {
        query
    }

    def JsonToQuery(j: JsonObject): Map[String, Vector[String]] = {
        j.obj.mapValues(arr=>{
            arr.arr.view.map(value=> Try(value.str).toOption).filter(_.isDefined).map(_.get).toVector
        }).toMap
    }

    case class RegisterMessage(name: String)

    abstract class HubConnector(path: String) {
        private val refconnector = this
        val socket: WebSocketClient = new WebSocketClient(new URI(path), new Draft_6455) {
            override def onOpen(handshakedata: ServerHandshake): Unit = refconnector.onOpen(handshakedata)

            override def onMessage(message: String): Unit = refconnector.onMessage(message)

            override def onMessage(bytes: ByteBuffer): Unit = refconnector.onMessageBytes(bytes)

            override def onClose(code: Int, reason: String, remote: Boolean): Unit = refconnector.onClose(code, reason, remote)

            override def onError(ex: Exception): Unit = refconnector.onError(ex)
        }

        def Connect(): Unit = {
            socket.connectBlocking()
        }

        def onMessageBytes(bytes: ByteBuffer): Unit

        def onMessage(msg: String): Unit = ???

        def onOpen(handshakedata: ServerHandshake): Unit = {}

        def onError(ex: Exception): Unit

        def onClose(code: Int, reason: String, remote: Boolean): Unit

        def send(text: String): Unit = socket.send(text)

        def send(buf: Array[Byte]): Unit = socket.send(buf)
    }

    abstract class Hub(path: String, hubName: String) extends HubConnector(path) {
        private var index = 1
        private val promiseMap = new mutable.HashMap[Int, Promise[JsonObject]]()

        val File = new Hub.File(this)

        private def CompletePromise(i: Int, result: JsonObject): Unit = {
            promiseMap.remove(i).foreach(p => {
                p.success(result)
            })
        }

        override def onMessageBytes(bytes: ByteBuffer): Unit = bytes.array().as[ExternalMessage] match {
            case Left(_) =>
            case Right(ExternalMessage(responseIndex, Some("error"), _, bodyOpt, sessionID)) =>
                println(s"error:${bodyOpt.get.toStringUtf8}")
                println("...")
            case Right(ExternalMessage(responseIndex, Some("register"), _, bodyOpt, sessionID)) =>
                println("up")
            case Right(ExternalMessage(responseIndex, Some(response), _, bodyOpt, sessionID)) =>
                val body = bodyOpt.map(bs => {
                    ujson.read(new String(bs.toByteArray, StandardCharsets.UTF_8)) match {
                        case obj: JsonObject => obj
                        case _ => Js.Obj()
                    }
                }).getOrElse(Js.Obj())
                println(s"complete index $responseIndex with ${body.toString}")
                CompletePromise(responseIndex, body)
            case Right(ExternalMessage(requestIndex, None, Some("uri"), bodyOpt, sessionID)) =>
                bodyOpt.flatMap(bs => bs.toByteArray.as[Message.UriMessage].toOption) match {
                    case Some(Message.UriMessage(hubName, action, target, query, body)) =>
                        val bodyJson = body.map(t => {
                            ujson.read(t) match {
                                case obj: Js.Obj => obj
                                case _ => Js.Obj()
                            }
                        })
                        val queryObj = JsonToQuery(query.map(s => ujson.read(s) match {
                            case obj: Js.Obj => obj
                            case _ => Js.Obj()
                        }).getOrElse(Js.Obj()))
                        handleUriRequest(action, target, queryObj, bodyJson, sessionID) onComplete {
                            case scala.util.Success(value) =>
                                val response = ExternalMessage(index, Some("uri"), None, Some(ByteString.copyFromUtf8(value.render())), sessionID)
                                send(response.asProtobufBytes)
                            case scala.util.Failure(exception) =>
                                val response = ExternalMessage(index, Some("error"), None, Some(ByteString.copyFromUtf8(exception.getCause.getLocalizedMessage)), sessionID)
                                send(response.asProtobufBytes)
                        }
                    case None =>
                        val responseBytes = Message.ExternalMessage(index, Some("error"), None, Some(ByteString.copyFromUtf8("body is not object")), sessionID).asProtobufBytes
                        send(responseBytes)
                }
            case _=>
                val responseBytes = Message.ExternalMessage(-1, Some("error"), None, Some(ByteString.copyFromUtf8("bad message")), "").asProtobufBytes
                send(responseBytes)
        }

        override def onOpen(handshakedata: ServerHandshake): Unit = {
            send(RegisterMessage("collection").asProtobufBytes)
        }

        override def onError(ex: Exception): Unit = {}

        override def onClose(code: Int, reason: String, remote: Boolean): Unit = {}

        def handleUriRequest(action: String, target: String, query: QueryObject, body: Option[JsonObject], sessionId: String): Future[JsonObject]

        def sendUriRequest(hubName: String, action: String, target: String, query: QueryObject, body: Option[JsonObject], sessionId: String): Future[JsonObject] = {
            val p = Promise[JsonObject]()
            val j = Message.UriMessage(hubName, action, target, Some(QueryToJson(query).render()), body.map(_.render()))
            val b = Message.ExternalMessage(index, None, Some("uri"), Some(ByteString.copyFrom(j.asProtobufBytes)), sessionId)
            promiseMap += (index -> p)
            index = index + 1
            send(b.asProtobufBytes)
            p.future
        }
    }
}
