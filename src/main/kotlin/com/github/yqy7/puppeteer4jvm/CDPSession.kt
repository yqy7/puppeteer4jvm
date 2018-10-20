package com.github.yqy7.puppeteer4jvm

import reactor.core.publisher.Mono

/**
 *  @author qiyun.yqy
 *  @date 2018/10/20
 */
class CDPSession(val connection: Connection, val targetType: String, val sessionId: String) : EventEmitter() {
    fun createRequestFrame(method: String): RequestFrame {
        return connection.createRequestFrame(method)
    }

    fun send(requestFrame: RequestFrame): Mono<ResponseFrame> {
        val targetRequestFrame = createRequestFrame("Target.sendMessageToTarget")
        targetRequestFrame.params
                .put("sessionId", sessionId)
                .put("message", requestFrame.toJson())
        targetRequestFrame.subFrameId = requestFrame.id
        return connection.send(targetRequestFrame)
    }
}