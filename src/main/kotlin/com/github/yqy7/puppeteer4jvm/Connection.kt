package com.github.yqy7.puppeteer4jvm

import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.readValue
import okhttp3.OkHttpClient
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.Request
import okhttp3.Response
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono
import reactor.core.publisher.ReplayProcessor
import reactor.core.publisher.toMono
import reactor.core.scheduler.Schedulers
import java.io.Closeable
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer

/**
 *  @author qiyun.yqy
 *  @date 2018/10/20
 */
class Connection private constructor(private val chromeProcess: Process,
                                     private val wsUrl: String) : EventEmitter(),Closeable {
    private val log = LoggerFactory.getLogger(this.javaClass)
    private val idGenerator = AtomicLong(0)
    private val messages = ReplayProcessor.create<ResponseFrame>(1024)
    private val sessions = mutableMapOf<String, CDPSession>()

    private val client = OkHttpClient()
    private lateinit var webSocket: WebSocket

    companion object {
        fun newConnection(chromeProcess: Process, wsUrl: String): Connection {
            val connection = Connection(chromeProcess, wsUrl).apply {
                val request = Request.Builder().url(wsUrl).build()
                val listener = object : WebSocketListener() {
                    override fun onMessage(webSocket: WebSocket?, text: String?) {
                        onMessage(text!!)
                    }

                    override fun onFailure(webSocket: WebSocket?, t: Throwable?, response: Response?) {
                        log.error("", t)
                    }
                }
                webSocket = client.newWebSocket(request, listener)
            }

            val requestFrame = connection.createRequestFrame("Target.setDiscoverTargets")
            requestFrame.params.put("discover", true) //false会导致不常生createTarget事件

            connection.send(requestFrame).block().error?.let { throw RuntimeException(it.message) }

            return connection
        }
    }

    fun onMessage(text: String) {
        if (log.isDebugEnabled) {
            log.debug("◀ RECV $text")
        }

        val responseFrame = JsonMapper.readValue<ResponseFrame>(text)
        if ("Target.receivedMessageFromTarget" == responseFrame.method) {
            val message = responseFrame.params!!.get("message").asText()
            onMessage(message)
        } else {
            messages.onNext(responseFrame)
        }
    }

    fun send(requestFrame: RequestFrame): Mono<ResponseFrame> {
        if (log.isDebugEnabled) {
            log.debug("SEND ► ${requestFrame.toJson()}")
        }

        return if (webSocket.send(requestFrame.toJson())) {
            messages.filter { responseFrame ->
                requestFrame.subFrameId?.let { responseFrame.isResponse(requestFrame.subFrameId) }
                        ?: responseFrame.isResponse(requestFrame.id)
            }.take(1).single()
        } else {
            RuntimeException("Websocket send message error!").toMono()
        }
    }

    fun on(event: String, consumer: Consumer<ResponseFrame>) {
        messages.filter { responseFrame -> responseFrame.isEvent(event) }
                .subscribeOn(Schedulers.elastic())
                .subscribe(consumer)
    }

    fun createRequestFrame(method: String): RequestFrame {
        return RequestFrame(idGenerator.incrementAndGet(), method)
    }

    fun createSession(targetInfo: ObjectNode): CDPSession {
        val requestFrame = createRequestFrame("Target.attachToTarget")
        requestFrame.params.put("targetId", targetInfo.get("targetId").asText())
        val responseFrame = send(requestFrame).block()
        val sessionId = responseFrame!!.result!!.get("sessionId").asText()

        val session = CDPSession(this, targetInfo.get("type").asText(), sessionId)
        // 订阅事件
        session.subscribeFrom(messages
                .filter { (id, result, error, method, params) -> responseFrame.isEvent() }
                .map { (id, result, error, method, params) -> Event(responseFrame.method!!, responseFrame.params) })
        sessions[sessionId] = session
        return session
    }

    override fun close() {
        emit("Connection.Events.Disconnected", objectNode())
        chromeProcess.destroyForcibly()
    }
}