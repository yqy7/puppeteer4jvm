package com.github.yqy7.puppeteer4jvm

/**
 *  @author qiyun.yqy
 *  @date 2018/10/20
 */
class BrowserContext(
        private val browser: Browser,
        private val id: String?,
        private val connection: Connection) {

    fun newPage(): Page {
        return browser.createPageInContext(id)
    }
}

class ElementHandle