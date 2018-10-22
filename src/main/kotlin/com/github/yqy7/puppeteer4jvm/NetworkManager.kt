package com.github.yqy7.puppeteer4jvm

import com.fasterxml.jackson.databind.node.ObjectNode
import java.util.function.Consumer

/**
 *  @author qiyun.yqy
 *  @date 2018/10/20
 */
private val statusTexts = mapOf(
        "100" to "Continue",
        "101" to "Switching Protocols",
        "102" to "Processing",
        "200" to "OK",
        "201" to "Created",
        "202" to "Accepted",
        "203" to "Non-Authoritative Information",
        "204" to "No Content",
        "206" to "Partial Content",
        "207" to "Multi-Status",
        "208" to "Already Reported",
        "209" to "IM Used",
        "300" to "Multiple Choices",
        "301" to "Moved Permanently",
        "302" to "Found",
        "303" to "See Other",
        "304" to "Not Modified",
        "305" to "Use Proxy",
        "306" to "Switch Proxy",
        "307" to "Temporary Redirect",
        "308" to "Permanent Redirect",
        "400" to "Bad Request",
        "401" to "Unauthorized",
        "402" to "Payment Required",
        "403" to "Forbidden",
        "404" to "Not Found",
        "405" to "Method Not Allowed",
        "406" to "Not Acceptable",
        "407" to "Proxy Authentication Required",
        "408" to "Request Timeout",
        "409" to "Conflict",
        "410" to "Gone",
        "411" to "Length Required",
        "412" to "Precondition Failed",
        "413" to "Payload Too Large",
        "414" to "URI Too Long",
        "415" to "Unsupported Media Type",
        "416" to "Range Not Satisfiable",
        "417" to "Expectation Failed",
        "418" to "I\"m a teapot",
        "421" to "Misdirected Request",
        "422" to "Unprocessable Entity",
        "423" to "Locked",
        "424" to "Failed Dependency",
        "426" to "Upgrade Required",
        "428" to "Precondition Required",
        "429" to "Too Many Requests",
        "431" to "Request Header Fields Too Large",
        "451" to "Unavailable For Legal Reasons",
        "500" to "Internal Server Error",
        "501" to "Not Implemented",
        "502" to "Bad Gateway",
        "503" to "Service Unavailable",
        "504" to "Gateway Timeout",
        "505" to "HTTP Version Not Supported",
        "506" to "Variant Also Negotiates",
        "507" to "Insufficient Storage",
        "508" to "Loop Detected",
        "510" to "Not Extended",
        "511" to "Network Authentication Required"
)

class NetworkManager(private val session: CDPSession) : EventEmitter() {
    private var frameManager: FrameManager? = null
    private val requestIdToRequest = mutableMapOf<String, Request>()
    private var extraHTTPHeaders = mutableMapOf<String, String>()
    private var offline = false
    private var credentials: Credentials? = null
    private var userRequestInterceptionEnabled = false
    private var protocolRequestInterceptionEnabled = false
    private var requestHashToRequestIds = multimapOf<String, String>()
    private var requestHashToInterceptionIds = multimapOf<String, String>()


    companion object {
        fun newNetworkManager(session: CDPSession): NetworkManager {
            val networkManager = NetworkManager(session)

            with(session) {
                on("Network.requestWillBeSent", Consumer(networkManager::onRequestWillBeSent))
                on("Network.requestIntercepted", Consumer(networkManager::onRequestIntercepted))
                on("Network.requestServedFromCache", Consumer(networkManager::onRequestServedFromCache))
                on("Network.responseReceived", Consumer(networkManager::onResponseReceived))
                on("Network.loadingFinished", Consumer(networkManager::onLoadingFinished))
                on("Network.loadingFailed", Consumer(networkManager::onLoadingFailed))
            }

            return networkManager
        }
    }

    fun onRequest(event: Event, interceptionId: String?) {
        val redirectChain = listOf<Request>()
        val data = event.data!!
        if (data.get("redirectResponse") != null) {
            val request = requestIdToRequest[data.get("requestId").asText()]
            if (request != null) {
                handleRequestRedirect(request, data.with("redirectResponse"))
//                redirectChain = request
            }
        }
    }

    fun handleRequestRedirect(request: Request, responsePayload: ObjectNode) {

    }

    fun onRequestWillBeSent(event: Event) {
        if (protocolRequestInterceptionEnabled) {
            return
        }


    }

    fun onRequestIntercepted(event: Event) {

    }

    fun onRequestServedFromCache(event: Event) {

    }

    fun onResponseReceived(event: Event) {

    }

    fun onLoadingFinished(event: Event) {

    }

    fun onLoadingFailed(event: Event) {

    }

    fun setFrameManager(frameManager: FrameManager) {
        this.frameManager = frameManager
    }

    fun setExtraHTTPHeaders(extraHTTPHeaders: Map<String, String>) {
        this.extraHTTPHeaders = mutableMapOf()
        for ((k, v) in extraHTTPHeaders) {
            this.extraHTTPHeaders[k.toLowerCase()] = v
        }

        val requestFrame = session.createRequestFrame("Network.setExtraHTTPHeaders")
        requestFrame.params.set("headers", objectNode(this.extraHTTPHeaders))
        session.send(requestFrame).block()
    }

    fun extraHTTPHeaders(): Map<String, String> {
        return extraHTTPHeaders
    }

    fun setOfflineMode(value: Boolean) {
        if (this.offline == value) return

        this.offline = value
        val requestFrame = session.createRequestFrame("Network.emulateNetworkConditions")
        requestFrame.params
                .put("offline", this.offline)
                // values of 0 remove any active throttling. crbug.com/456324#c9
                .put("latency", 0)
                .put("downloadThroughput", -1)
                .put("uploadThroughput", -1)

        session.send(requestFrame).block()
    }

    fun setUserAgent(userAgent: String) {
        val requestFrame = session.createRequestFrame("Network.setUserAgentOverride")
        requestFrame.params.put("userAgent", userAgent)
        session.send(requestFrame).block()
    }

    fun setRequestInterception(value: Boolean) {
        this.userRequestInterceptionEnabled = value
        updateProtocolRequestInterception()
    }

    fun updateProtocolRequestInterception() {
        val enabled = userRequestInterceptionEnabled || credentials != null
        if (enabled == protocolRequestInterceptionEnabled) return
        protocolRequestInterceptionEnabled = enabled
        val patterns = if (enabled) listOf(mapOf("urlPattern" to "*")) else listOf()

        val setCacheDisabledRequestFrame = session.createRequestFrame("Network.setCacheDisabled")
        setCacheDisabledRequestFrame.params.put("cacheDisabled", enabled)
        session.send(setCacheDisabledRequestFrame).block()

        val setRequestInterceptionRequestFrame = session.createRequestFrame("Network.setRequestInterception")
        setRequestInterceptionRequestFrame.params.put("patterns", objectNode(patterns))
        session.send(setRequestInterceptionRequestFrame).block()
    }

}

data class ContinueOptions(
        val url: String,
        val method: String,
        val postData: String,
        val headers: Map<String, String>
)

data class RespondOptions(
        val status: Int,
        val headers: Map<String, String>,
        val contentType: String,
        val body: String?
)

val errorReasons = mapOf(
        "aborted" to "Aborted",
        "accessdenied" to "AccessDenied",
        "addressunreachable" to "AddressUnreachable",
        "blockedbyclient" to "BlockedByClient",
        "blockedbyresponse" to "BlockedByResponse",
        "connectionaborted" to "ConnectionAborted",
        "connectionclosed" to "ConnectionClosed",
        "connectionfailed" to "ConnectionFailed",
        "connectionrefused" to "ConnectionRefused",
        "connectionreset" to "ConnectionReset",
        "internetdisconnected" to "InternetDisconnected",
        "namenotresolved" to "NameNotResolved",
        "timedout" to "TimedOut",
        "failed" to "Failed"
)

class Request(
        val session: CDPSession,
        val frame: Frame,
        val interceptionId: String,
        val allowInterception: Boolean,
        event: ObjectNode,
        val redirectChain: List<Request>
) {
    private val requestId = event.get("requestId").asText()
    private val isNavigationRequest = event.get(requestId).asText() == event.get("loaderId").asText()
            && event.get("type").asText() == "Document"
    private var interceptionHandled = false
    private val response: Response? = null
    private val failureText: String? = null
    private val url = event.with("request").get("url").asText()
    private val resourceType = event.get("type").asText().toLowerCase()
    private val method = event.with("request").get("method").asText()
    private val postData: String? = event.with("request").get("postData").asText()
    private val fromMemoryCache = false
    private val headers = mutableMapOf<String, String>()

    init {
        val eventRequestHeaders = event.with("request").with("headers")
        for (fieldName in eventRequestHeaders.fieldNames()) {
            headers[fieldName.toLowerCase()] = eventRequestHeaders.get(fieldName).asText()
        }
    }

    fun url(): String {
        return url
    }

    fun resourceType(): String {
        return resourceType
    }

    fun method(): String {
        return method
    }

    fun postData(): String? {
        return postData
    }


    fun headers(): Map<String, String> {
        return headers
    }

    fun response(): Response? {
        return response
    }

    fun frame(): Frame {
        return frame
    }

    fun isNavigationRequest(): Boolean {
        return isNavigationRequest
    }

    fun redirectChain(): List<Request> {
        return redirectChain
    }

    fun failure(): String? {
        return failureText
    }

    fun `continue`(overrides: ContinueOptions) {
        interceptionHandled = true
        val requestFrame = session.createRequestFrame("Network.continueInterceptedRequest")
        requestFrame.params
                .put("interceptionId", interceptionId)
                .put("url", overrides.url)
                .put("method", overrides.method)
                .put("postData", overrides.postData)
                .set("headers", objectNode(overrides.headers))
        session.send(requestFrame).block()
    }

    fun respond(response: RespondOptions) {

    }

    fun abort(errorCode: String = "failed") {

    }

}

data class RemoteAddress(
        val ip: String,
        val port: Int
)

class Response(
        private val session: CDPSession,
        private val request: Request,
        responsePayload: ObjectNode
) {
    private val remoteAddress = RemoteAddress(responsePayload.get("remoteIPAddress").asText(),
            responsePayload.get("remotePort").asInt())
    private val status = responsePayload.get("status").asInt()
    private val statusText = responsePayload.get("statusText").asText()
    private val url = request.url()
    private val fromDiskCache = responsePayload.get("fromDiskCache").asBoolean()
    private val fromServiceWorker = responsePayload.get("fromServiceWorker").asBoolean()
    private val headers = mutableMapOf<String, String>()
    init {
        val headersPayload = responsePayload.with("headers")
        for (fieldName in headersPayload.fieldNames()) {
            headers[fieldName.toLowerCase()] = headersPayload.get(fieldName).asText()
        }
    }
}

data class Credentials(
        val username: String,
        val password: String
)

class SecurityDetails(securityPayload: ObjectNode) {
    private val subjectName: String? = securityPayload.get("subjectName").asText()
    private val issuer: String? = securityPayload.get("issuer").asText()
    private val validFrom: Long? = securityPayload.get("validFrom").asLong()
    private val validTo: Long? = securityPayload.get("validTo").asLong()
    private val protocol: String? = securityPayload.get("protocol").asText()


    fun subjectName(): String? {
        return subjectName
    }

    fun issuer(): String? {
        return issuer
    }

    fun validFrom(): Long? {
        return validFrom
    }

    fun validTo(): Long? {
        return validTo
    }

    fun protocol(): String? {
        return protocol
    }
}