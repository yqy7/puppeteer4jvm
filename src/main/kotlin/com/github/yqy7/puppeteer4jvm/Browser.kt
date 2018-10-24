package com.github.yqy7.puppeteer4jvm

import com.fasterxml.jackson.databind.node.ObjectNode
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.lang.RuntimeException
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer
import java.util.function.Supplier

/**
 *  @author qiyun.yqy
 *  @date 2018/10/19
 */

private const val CREATE_TARGET_TIME = 10_000

class Browser private constructor(
        private val connection: Connection,
        private val ignoreHTTPSErrors: Boolean,
        private val defaultViewport: Viewport?,
        private val browserContextIds: List<Long>?
) : EventEmitter(), Closeable {
    private val log = LoggerFactory.getLogger(this.javaClass)

    private val contexts = mutableMapOf<String, BrowserContext>()
    private val defaultContext = BrowserContext(this, null, connection)
    private val targets = ConcurrentHashMap<String, Target>()

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
        val browserContextId = targetInfo.get("browserContextId")?.asText()
        val context = browserContextId?.let { contexts[browserContextId] } ?: defaultContext

        val target = Target(targetInfo, context, Supplier { connection.createSession(targetInfo) }, ignoreHTTPSErrors, defaultViewport)

        val targetId = targetInfo.get("targetId").asText()
        targets[targetId] = target

        emit("targetcreated", target)
    }

    fun targetDestroyed(responseFrame: ResponseFrame) {

    }

    fun targetInfoChanged(responseFrame: ResponseFrame) {

    }

    fun newPage(): Page {
        return defaultContext.newPage()
    }

    fun createPageInContext(contextId: String?): Page {
        val requestFrame = connection.createRequestFrame("Target.createTarget")
        requestFrame.params.put("url", "about:blank")
        if (contextId != null) {
            requestFrame.params.put("browserContextId", contextId)
        }

        val responseFrame = connection.send(requestFrame).block()
        val targetId = responseFrame.result!!.get("targetId").asText()
        var target = targets[targetId]

        // 自旋等待创建完target
        val start = System.currentTimeMillis()
        while (target == null && System.currentTimeMillis() - start <= CREATE_TARGET_TIME) {
            Thread.yield()
            target = targets[targetId]
        }

        if (target == null) {
            throw RuntimeException("Create page timeout!")
        }

        return target!!.page()!!
    }

    fun targets(): List<Target> {
        return targets.values.filter { it.isInitialized }
    }


    fun disposeContext(contextId: String) {
        val requestFrame = connection.createRequestFrame("Target.disposeBrowserContext")
        if (contextId != null) {
            requestFrame.params.put("browserContextId", contextId)
        }
        connection.send(requestFrame).block()
        contexts.remove(contextId)
    }

    fun browserContexts(): List<BrowserContext> {
        return contexts.values  + defaultContext
    }

    fun defaultBrowserContext(): BrowserContext {
        return defaultContext
    }

    fun target(): Target? {
        return targets().find { it.type() == "browser" }
    }

    fun pages(): List<Page> {
        return browserContexts().flatMap { it.pages() }
    }

    fun version(): String {
        val version = getVersion()
        return version.get("product").asText()
    }

    fun userAgent(): String {
        val version = getVersion()
        return version.get("userAgent").asText()
    }

    override fun close() {
        disconnect()
    }

    fun disconnect() {
        connection.dispose()
    }

    fun getVersion(): ObjectNode {
        val requestFrame = connection.createRequestFrame("Browser.getVersion")
        val (_, result) = connection.send(requestFrame).block()
        return result!!
    }

    class Events {
        val TargetCreated = "targetcreated"
        val TargetDestroyed = "targetdestroyed"
        val TargetChanged = "targetchanged"
        val Disconnected = "disconnected"
    }
}