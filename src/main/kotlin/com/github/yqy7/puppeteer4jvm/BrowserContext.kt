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

    fun targets(): List<Target> {
        return browser.targets().filter { it.browserContext() === this }
    }

    fun pages(): List<Page> {
        return targets().filter { it.type() == "page" && it.page() != null }.map { it.page()!! }
    }

    fun isIncognito(): Boolean {
        return id != null
    }

    fun overridePermissions(origin: String, permissions: List<String>) {
        val protocolPermissions = permissions.map { webPermissionToProtocol[id] }
        val requestFrame = connection.createRequestFrame("Browser.grantPermissions")
        requestFrame.params.put("origin", origin).set("permissions", objectNode(protocolPermissions))
        if (id != null) {
            requestFrame.params.put("browserContextId", id)
        }

        connection.send(requestFrame).block()
    }

    fun clearPermissionOverrides() {
        val requestFrame = connection.createRequestFrame("Browser.resetPermissions")
        if (id != null) {
            requestFrame.params.put("browserContextId", id)
        }

        connection.send(requestFrame).block()
    }

    fun browser(): Browser {
        return browser
    }

    fun close() {
        id ?: error("Non-incognito profiles cannot be closed!")
        browser.disposeContext(id)
    }

    class Events {
        val TargetCreated = "targetcreated"
        val TargetDestroyed = "targetdestroyed"
        val TargetChanged = "targetchanged"
    }
}

val webPermissionToProtocol = mapOf(
"geolocation" to "geolocation",
"midi" to "midi",
"notifications" to "notifications",
"push" to "push",
"camera" to "videoCapture",
"microphone" to "audioCapture",
"background-sync" to "backgroundSync",
"ambient-light-sensor" to "sensors",
"accelerometer" to "sensors",
"gyroscope" to "sensors",
"magnetometer" to "sensors",
"accessibility-events" to "accessibilityEvents",
"clipboard-read" to "clipboardRead",
"clipboard-write" to "clipboardWrite",
"payment-handler" to "paymentHandler",
// chrome-specific permissions we have.
"midi-sysex" to "midiSysex"
)
