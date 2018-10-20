package com.github.yqy7.puppeteer4jvm

import java.io.Closeable
import java.util.function.Consumer
import java.util.function.Supplier

/**
 *  @author qiyun.yqy
 *  @date 2018/10/19
 */
class Browser private constructor(
        private val connection: Connection,
        private val ignoreHTTPSErrors: Boolean,
        private val defaultViewport: Viewport?,
        private val browserContextIds: List<Long>?
) : EventEmitter(), Closeable {
    private val contexts = mutableMapOf<Long, BrowserContext>()
    private val defaultContext = BrowserContext(this, null, connection)
    private val targets = mutableMapOf<String, Target>()

    companion object {
        fun newBrowser(connection: Connection, ignoreHTTPSErrors: Boolean, defaultViewport: Viewport?, browserContextIds: List<Long>?): Browser {
            val browser = Browser(connection, ignoreHTTPSErrors, defaultViewport, browserContextIds)

            with(connection) {
                on("Target.targetCreated", Consumer(browser::targetCreated))
                on("Target.targetDestroyed", Consumer(browser::targetDestroyed))
                on("Target.targetInfoChanged", Consumer(browser::targetInfoChanged))
            }

            return browser
        }
    }

    fun targetCreated(responseFrame: ResponseFrame) {
        val targetInfo = responseFrame.params!!.with("targetInfo")
        val browserContextId = targetInfo.get("browserContextId")?.asLong()
        val context = contexts[browserContextId] ?: defaultContext

        val target = Target(targetInfo, context, Supplier { connection.createSession(targetInfo) }, ignoreHTTPSErrors, defaultViewport)

        targets[targetInfo.get("targetId").asText()] = target

        emit("targetcreated", target)
    }

    fun targetDestroyed(responseFrame: ResponseFrame) {

    }

    fun targetInfoChanged(responseFrame: ResponseFrame) {

    }

    fun newPage(): Page {
        return defaultContext.newPage()
    }

    fun createPageInContext(contextId: Long?): Page {
        val requestFrame = connection.createRequestFrame("Target.createTarget")
        requestFrame.params.put("url", "http://www.baidu.com")
        if (contextId != null) {
            requestFrame.params.put("browserContextId", contextId)
        }

        val responseFrame = connection.send(requestFrame).block()
        val targetId = responseFrame.result!!.get("targetId").asText()
        val target = targets[targetId]
        return target!!.page()
    }

    override fun close() {
        connection.close()
    }
}