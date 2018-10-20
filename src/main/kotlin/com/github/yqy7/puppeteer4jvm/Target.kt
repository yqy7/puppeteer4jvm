package com.github.yqy7.puppeteer4jvm

import com.fasterxml.jackson.databind.node.ObjectNode
import java.util.function.Supplier

/**
 *  @author qiyun.yqy
 *  @date 2018/10/20
 */
class Target(private val targetInfo: ObjectNode,
             private val browserContext: BrowserContext,
             private val sessionFactory: Supplier<CDPSession>,
             private val ignoreHTTPSErrors: Boolean,
             private val defaultViewport: Viewport?) {
    private val isInitialized = "type" != targetInfo.get("type").asText() || "" != targetInfo.get("type").asText()
    private var page: Page? = null
    val targetId = targetInfo.get("targetId").asText()

    fun page(): Page {
        val type = targetInfo.get("type").asText()
        if (("page" == type || "background_page" == type) && page == null) {
            val session = sessionFactory.get()
            page = Page.newPage(session, this, ignoreHTTPSErrors, defaultViewport)
        }
        return page!!
    }

    fun browserContext(): BrowserContext {
        return browserContext
    }

    fun type(): String {
        val type = targetInfo.get("type").asText()
        return if ("page" == type
                || "background_page" == type
                || "service_worker" == type
                || "browser" == type) {
            type
        } else {
            "other"
        }
    }
}