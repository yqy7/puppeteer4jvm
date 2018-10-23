package com.github.yqy7.puppeteer4jvm

import com.fasterxml.jackson.databind.node.ObjectNode
import java.nio.charset.Charset
import java.util.*
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
    private var requestIdToRequestWillBeSentEvent = mutableMapOf<String, ObjectNode>()
    private var offline = false
    private var credentials: Credentials? = null
    private var userRequestInterceptionEnabled = false
    private var protocolRequestInterceptionEnabled = false
    private var requestHashToRequestIds = multimapOf<String, String>()
    private var requestHashToInterceptionIds = multimapOf<String, String>()
    private var attemptedAuthentications = mutableSetOf<String>()


    companion object {
        fun newNetworkManager(session: CDPSession): NetworkManager {
            val networkManager = NetworkManager(session)

            networkManager.session.on("Network.requestWillBeSent", Consumer(networkManager::onRequestWillBeSent))
            networkManager.session.on("Network.requestIntercepted", Consumer(networkManager::onRequestIntercepted))
            networkManager.session.on("Network.requestServedFromCache", Consumer(networkManager::onRequestServedFromCache))
            networkManager.session.on("Network.responseReceived", Consumer(networkManager::onResponseReceived))
            networkManager.session.on("Network.loadingFinished", Consumer(networkManager::onLoadingFinished))
            networkManager.session.on("Network.loadingFailed", Consumer(networkManager::onLoadingFailed))

            return networkManager
        }
    }

    fun handleRequestRedirect(request: Request, responsePayload: ObjectNode) {
        val response = Response(session, request, responsePayload)
        request.response = response
        request.redirectChain.add(request)
        requestIdToRequest.remove(request.requestId)

        emit(NetworkManager.Events.Response, response)
        emit(NetworkManager.Events.RequestFinished, request)
    }

    /**
     * 请求发送之前
     */
    fun onRequestWillBeSent(event: Event) {
        val (_, result) = event
        result as ObjectNode

        if (protocolRequestInterceptionEnabled) {
            val requestHash = generateRequestHash(result.with("request"))
            val interceptionId = requestHashToInterceptionIds.firstValue(requestHash)
            if (interceptionId != null) {
                onRequest(result, interceptionId)
                requestHashToInterceptionIds.remove(requestHash, interceptionId);
            } else {
                requestHashToRequestIds.put(requestHash, result.get("requestId").asText())
                requestIdToRequestWillBeSentEvent.put(result.get("requestId").asText(), result)
            }
            return
        }

        onRequest(result, null)
    }

    fun onRequest(eventPayload: ObjectNode, interceptionId: String?) {
        var redirectChain = mutableListOf<Request>()

        // 处理重定向情况
        if (eventPayload.get("redirectResponse") != null) {
            val request = requestIdToRequest[eventPayload.get("requestId").asText()]
            if (request != null) {
                handleRequestRedirect(request, eventPayload.with("redirectResponse"))
                redirectChain = request.redirectChain
            }
        }

        val frame = if (eventPayload.get("frameId") != null
                && frameManager != null) frameManager!!.frame(eventPayload.get("frameId").asText()) else null
        val request = Request(session, frame, interceptionId, userRequestInterceptionEnabled, eventPayload, redirectChain)
        requestIdToRequest[eventPayload.get("requestId").asText()] = request

        emit(NetworkManager.Events.Request, request)
    }

    /**
     * 请求拦截
     */
    fun onRequestIntercepted(event: Event) {
        val (_, result) = event
        result as ObjectNode

        if (result.get("authChallenge") != null) {
            // "Default"|"CancelAuth"|"ProvideCredentials"
            val interceptionId = result.get("interceptionId").asText()
            var response = "Default"
            if (attemptedAuthentications.contains(interceptionId)) {
                response = "CancelAuth"
            } else if (credentials != null) {
                response = "ProvideCredentials"
                attemptedAuthentications.add(interceptionId)
            }

            val requestFrame = session.createRequestFrame("Network.continueInterceptedRequest")
            val authChallengeResponse = objectNode()
            authChallengeResponse.put("response", response)
            credentials?.username?.let { authChallengeResponse.put("username", it) }
            credentials?.password?.let { authChallengeResponse.put("password", it) }

            requestFrame.params
                    .put("interceptionId", interceptionId)
                    .set("authChallengeResponse", authChallengeResponse)

            session.send(requestFrame).block()
            return
        }

        if (!userRequestInterceptionEnabled && protocolRequestInterceptionEnabled) {
            val requestFrame = session.createRequestFrame("Network.continueInterceptedRequest")
            requestFrame.params.put("interceptionId", result.get("interceptionId").asText())
            session.send(requestFrame).block()
        }

        val requestHash = generateRequestHash(result.with("request"))
        val requestId = requestHashToRequestIds.firstValue(requestHash)
        if (requestId != null) {
            val requestWillBeSentEvent = requestIdToRequestWillBeSentEvent.get(requestId)
            onRequest(requestWillBeSentEvent!!, result.get("interceptionId").asText())
            requestHashToRequestIds.remove(requestHash, requestId)
            requestIdToRequestWillBeSentEvent.remove(requestId)
        } else {
            requestHashToInterceptionIds.put(requestHash, result.get("interceptionId").asText());
        }
    }

    /**
     * 请求为从缓存中获取
     */
    fun onRequestServedFromCache(event: Event) {
        val (_, result) = event
        result as ObjectNode
        val request = requestIdToRequest.get(result.get("requestId").asText())
        if (request != null) {
            request.fromMemoryCache = true
        }
    }

    /**
     * 收到响应
     */
    fun onResponseReceived(event: Event) {
        val (_, result) = event
        result as ObjectNode

        val request = requestIdToRequest.get(result.get("requestId").asText()) ?: return

        val response = Response(session, request, result.with("response"))
        request.response = response
        emit(NetworkManager.Events.Response, response)
    }

    /**
     * 加载结束
     */
    fun onLoadingFinished(event: Event) {
        val (_, result) = event
        result as ObjectNode

        // For certain requestIds we never receive requestWillBeSent event.
        // @see https://crbug.com/750469
        val request = requestIdToRequest.get(result.get("requestId").asText())
        if (request == null) {
            return
        }

        // Under certain conditions we never get the Network.responseReceived
        // event from protocol. @see https://crbug.com/883475
        if (request.response() != null) {

        }

        requestIdToRequest.remove(request.requestId)
//        attemptedAuthentications
        emit(NetworkManager.Events.RequestFinished, request)
    }

    /**
     * 加载失败
     */
    fun onLoadingFailed(event: Event) {
        val (_, result) = event
        result as ObjectNode

        // For certain requestIds we never receive requestWillBeSent event.
        // @see https://crbug.com/750469
        val request = requestIdToRequest.get(result.get("requestId").asText()) ?: return

        request.failureText = result.get("errorText").asText()
        val response = request.response
//        if (response)
//            response._bodyLoadedPromiseFulfill.call(null);
        requestIdToRequest.remove(request.requestId)
//        _attemptedAuthentications.delete(request._interceptionId);
        emit(NetworkManager.Events.RequestFailed, request)
    }

    fun setFrameManager(frameManager: FrameManager) {
        this.frameManager = frameManager
    }

    fun authenticate(credentials: Credentials) {
        this.credentials = credentials
        updateProtocolRequestInterception()
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
        setRequestInterceptionRequestFrame.params.set("patterns", objectNode(patterns))
        session.send(setRequestInterceptionRequestFrame).block()
    }

    class Events {
        companion object {
            val Request = "request"
            val Response = "response"
            val RequestFailed = "requestfailed"
            val RequestFinished = "requestfinished"
        }
    }

}

data class ContinueOptions(
        val url: String,
        val method: String,
        val postData: String,
        val headers: Map<String, String>
)

data class RespondOptions(
        val status: Int?,
        val headers: Map<String, String>?,
        val contentType: String?,
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
        val frame: Frame?,
        val interceptionId: String?,
        val allowInterception: Boolean,
        event: ObjectNode,
        val redirectChain: MutableList<Request>
) {
    val requestId = event.get("requestId").asText()
    private val isNavigationRequest = requestId == event.get("loaderId").asText()
            && event.get("type").asText() == "Document"
    private var interceptionHandled = false
    var response: Response? = null
    var failureText: String? = null
    val url = event.with("request").get("url").asText()
    private val resourceType = event.get("type").asText().toLowerCase()
    private val method = event.with("request").get("method").asText()
    private val postData: String? = event.with("request").get("postData")?.asText()
    var fromMemoryCache = false
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

    fun frame(): Frame? {
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
        if (url.startsWith("data:"))
            return

        if (!allowInterception) error("Request Interception is not enabled!")
        if (interceptionHandled) error("Request is already handled!")

        interceptionHandled = true

        val responseBody = response.body?.toByteArray()

        val responseHeaders = mutableMapOf<String, String>()
        if (response.headers != null) {
            for (key in response.headers.keys) {
                responseHeaders[key.toLowerCase()] = response.headers[key]!!
            }
        }

        if (response.contentType != null) {
            responseHeaders["content-type"] = response.contentType
        }

        if (responseBody != null && ("content-length" !in responseHeaders)) {
            responseHeaders["content-length"] = responseBody.size.toString()
        }

        val statusCode = response.status ?: 200
        val statusText = statusTexts[statusCode.toString()] ?: ""
        val statusLine = "HTTP/1.1 $statusCode $statusText"

        val CRLF = "\r\n"
        var text = statusLine + CRLF
        var responseBuffer = text.toByteArray(Charset.forName("UTF-8"))
        if (responseBody != null) {
            responseBuffer = responseBuffer.plus(responseBody)
        }

        val requestFrame = session.createRequestFrame("Network.continueInterceptedRequest")
        requestFrame.params.put("interceptionId", interceptionId).put("rawResponse", Base64.getEncoder().encode(responseBuffer))
        session.send(requestFrame).block()
    }

    fun abort(errorCode: String = "failed") {
        val errorReason = errorReasons[errorCode] ?: error("Unknown error code: $errorCode")
        if (!allowInterception) error("Request Interception is not enabled!")
        if (interceptionHandled) error("Request is already handled!")

        interceptionHandled = true

        val requestFrame = session.createRequestFrame("Network.continueInterceptedRequest")
        requestFrame.params.put("interceptionId", interceptionId).put("errorReason", "errorReason")

        session.send(requestFrame).block()
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
    private val securityDetails = if (responsePayload.get("securityDetails") != null)
        SecurityDetails(responsePayload.with("securityDetails")) else null

    init {
        val headersPayload = responsePayload.with("headers")
        for (fieldName in headersPayload.fieldNames()) {
            headers[fieldName.toLowerCase()] = headersPayload.get(fieldName).asText()
        }
    }

    fun remoteAddress(): RemoteAddress {
        return remoteAddress
    }

    fun url(): String {
        return url
    }

    fun ok(): Boolean {
        return status == 0 || (status in 200..299)
    }

    fun status(): Int {
        return status
    }

    fun statusText(): String {
        return statusText
    }

    fun headers(): MutableMap<String, String> {
        return headers
    }

    fun securityDetails(): SecurityDetails? {
        return securityDetails
    }

    // buffer/text/json
    fun buffer(): ByteArray {
        val requestFrame = session.createRequestFrame("Network.getResponseBody")
        requestFrame.params.put("requestId", request.requestId)
        val responseFrame = session.send(requestFrame).block()
        val (_, result) = responseFrame
        val body = result!!.get("body").asText()
        val base64Encoded = result!!.get("base64Encoded").asBoolean()
        return if (base64Encoded) {
            Base64.getDecoder().decode(body)
        } else {
            body.toByteArray(Charset.forName("UTF-8"))
        }
    }

    fun text(): String {
        val content = buffer()
        return content.toString(Charset.forName("UTF-8"))
    }

    fun json(): ObjectNode {
        return JsonMapper.readTree(text()) as ObjectNode
    }

    fun request(): Request {
        return request
    }

    fun fromCache(): Boolean {
        return fromDiskCache || request.fromMemoryCache
    }

    fun fromServiceWorker(): Boolean {
        return fromServiceWorker
    }

    fun frame(): Frame? {
        return request.frame()
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

fun generateRequestHash(requestPayload: ObjectNode): String {
    var normalizedURL = requestPayload.get("url").asText()
    try {
        normalizedURL = decodeUri(normalizedURL)
    } catch (e: Exception) {
    }

    val hash = objectNode()
    hash.put("url", normalizedURL)
            .put("method", requestPayload.get("method").asText())
            .put("postData", requestPayload.get("postData").asText())
    val headers = objectNode()
    if (!normalizedURL.startsWith("data:")) {
        val headersPayload = requestPayload.with("headers")
        val headerKeys = headersPayload.fieldNames().asSequence().sorted()

        for (key in headerKeys) {
            val headerKey = key.toLowerCase()
            if (headerKey == "accept" || headerKey == "referer" || headerKey == "x-devtools-emulate-network-conditions-client-id" || headerKey == "cookie")
                continue
            val headerValue = headersPayload.get(key).asText()
            headers.put(headerKey, headerValue)
        }
    }
    hash.set("headers", headers)
    return hash.toString()
}