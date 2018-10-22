package com.github.yqy7.puppeteer4jvm

import com.fasterxml.jackson.databind.node.ObjectNode
import java.io.File
import java.util.*


/**
 *  @author qiyun.yqy
 *  @date 2018/10/20
 */

fun fileExtensionToType(path: String): String {
    val extension = path.substring(path.lastIndexOf("."), path.length)
    return when (extension) {
        ".jpeg", ".jpg", ".jpe" -> "jpeg"
        else -> "png"
    }
}

val unitToPixels = mapOf(
        "px" to 1.0,
        "in" to 96.0,
        "cm" to 37.8,
        "mm" to 3.78
)

fun convertPrintParameterToInches(parameter: String?): Double? {
    if (parameter == null) return null

    var unit = parameter.substring(parameter.length - 2).toLowerCase()
    val valueText = if (unitToPixels.containsKey(unit)) {
        parameter.substring(0, parameter.length - 2)
    } else {
        unit = "px"
        parameter
    }

    val value = valueText.toDouble()
    return value * unitToPixels[unit]!! / 96
}

data class Color(
        var r: Int,
        var g: Int,
        var b: Int,
        var a: Int)

data class Clip(
        var x: Int,
        var y: Int,
        var width: Int,
        var height: Int,
        var scale: Int
)

data class ScreenshotOptions(
        var path: String?,
        var type: String? = "png",
        var quality: Int = 100,
        var fullPage: Boolean = true,
        var clip: Clip? = null,
        var omitBackground: Boolean = false,
        var encoding: String = "binary"
)

data class Margin(
        val top: String?,
        val right: String?,
        val bottom: String?,
        val left: String?
)

data class PdfOptions(
        val path: String?,
        val scale: Float = 1f,
        val displayHeaderFooter: Boolean = false,
        val headerTemplate: String? = null,
        val footerTemplate: String? = null,
        val printBackground: Boolean = false,
        val landscape: Boolean = false,
        val pageRanges: String? = null,
        val format: String? = null,
        val width: String? = null,
        val height: String? = null,
        val margin: Margin? = null,
        val preferCSSPageSize: Boolean = false
)

data class GotoOptions(
        var timeout: Int? = null,
        var waitUntil: List<String> = mutableListOf(),
        var referer: String? = null
)

data class Format(val width: Double, val height: Double)

val PaperFormats = mapOf(
        "letter" to Format(8.5, 11.0),
        "legal" to Format(8.5, 14.0),
        "tabloid" to Format(11.0, 17.0),
        "ledger" to Format(17.0, 11.0),
        "a0" to Format(33.1, 46.8),
        "a1" to Format(23.4, 33.1),
        "a2" to Format(16.5, 23.4),
        "a3" to Format(11.7, 16.5),
        "a4" to Format(8.27, 11.7),
        "a5" to Format(5.83, 8.27),
        "a6" to Format(4.13, 5.83)
)


class Page(private val session: CDPSession, private val target: Target, private val frameTree: ObjectNode) : EventEmitter() {
    private val networkManager = NetworkManager.newNetworkManager(session)
    private val frameManager = FrameManager.newFrameManager(session, frameTree, this, networkManager)
    private val emulationManager = EmulationManager.newEmulationManager(session)
    private var viewport: Viewport? = null

    companion object {
        fun newPage(session: CDPSession, target: Target, ignoreHTTPSErrors: Boolean, viewport: Viewport?): Page {
            session.send(session.createRequestFrame("Page.enable")).block()

            val responseFrame = session.send(session.createRequestFrame("Page.getFrameTree")).block()
            val frameTree = responseFrame.result!!.with("frameTree")

            val page = Page(session, target, frameTree)

            // init
            val requestFrame = session.createRequestFrame("Target.setAutoAttach")
            requestFrame.params.put("autoAttach", true).put("waitForDebuggerOnStart", false)
            session.send(requestFrame).block()

            val requestFrame1 = session.createRequestFrame("Page.setLifecycleEventsEnabled")
            requestFrame1.params.put("enabled", true)
            session.send(requestFrame1).block()

            session.send(session.createRequestFrame("Network.enable")).block()
            session.send(session.createRequestFrame("Runtime.enable")).block()
            session.send(session.createRequestFrame("Security.enable")).block()
            session.send(session.createRequestFrame("Performance.enable")).block()
            session.send(session.createRequestFrame("Log.enable")).block()

            if (ignoreHTTPSErrors) {
                val requestFrame2 = session.createRequestFrame("Security.setOverrideCertificateErrors")
                requestFrame2.params.put("override", true)
                session.send(requestFrame2).block()
            }

            if (viewport != null) {
                page.setViewport(viewport)
            }

            return page
        }
    }

    fun setViewport(viewport: Viewport) {
        this.viewport = viewport
        val needReload = emulationManager.emulateViewport(viewport)
        if (needReload) {
            reload()
        }
    }

    fun reload() {
        waitForNavigation()
        session.send(session.createRequestFrame("Page.reload")).block()
    }

    fun waitForNavigation() {

    }

    fun goto(url: String, options: GotoOptions) {
        frameManager.mainFrame()!!.goto(url, options)
    }

    fun screenshot(options: ScreenshotOptions): ByteArray {
        val type: String = options.type ?: options.path?.let { fileExtensionToType(it) } ?: "png"

        val activateTargetRequestFrame = session.createRequestFrame("Target.activateTarget")
        activateTargetRequestFrame.params.put("targetId", target.targetId)
        session.send(activateTargetRequestFrame).block()

        var clip: Clip? = null
        if (options.fullPage) {
            val requestFrame = session.createRequestFrame("Page.getLayoutMetrics")
            val (_, result) = session.send(requestFrame).block()
            val width = result!!.with("contentSize").get("width").asInt()
            val height = result!!.with("contentSize").get("height").asInt()
            clip = Clip(0, 0, width, height, 1)

            var isMobile = viewport?.isMobile ?: false
            var deviceScaleFactor = viewport?.deviceScaleFactor ?: 1.0f
            var isLandscape = viewport?.isLandscape ?: false

            val screenOrientation = objectNode()
            if (isLandscape) {
                screenOrientation.put("angle", 90).put("type", "landscapePrimary")
            } else {
                screenOrientation.put("angle", 0).put("type", "portraitPrimary")
            }

            val requestFrame1 = session.createRequestFrame("Emulation.setDeviceMetricsOverride")
            requestFrame1.params
                    .put("mobile", isMobile)
                    .put("width", width)
                    .put("height", height)
                    .put("deviceScaleFactor", deviceScaleFactor)
                    .set("screenOrientation", screenOrientation)
            session.send(requestFrame1).block()
        }

        val shouldSetDefaultBackground = options.omitBackground && "png" == type
        if (shouldSetDefaultBackground) {
            val requestFrame = session.createRequestFrame("Emulation.setDefaultBackgroundColorOverride")
            requestFrame.params.set("color", objectNode(Color(0, 0, 0, 0)))
            session.send(requestFrame).block()
        }

        val requestFrame = session.createRequestFrame("Page.captureScreenshot")
        requestFrame.params
                .put("format", "png")
                .put("quality", options.quality)

        clip?.let { requestFrame.params.set("clip", objectNode(clip)) }

        val (_, result) = session.send(requestFrame).block()

        if (shouldSetDefaultBackground) {
            session.send(session.createRequestFrame("Emulation.setDefaultBackgroundColorOverride")).block()
        }

        if (options.fullPage) {
            viewport?.let { setViewport(it) }
        }

        val data = result!!.get("data").asText()
        var dataArr = Base64.getDecoder().decode(data)

        options.path?.let {
            val image = File(options.path)
            image.parentFile.mkdirs()
            image.writeBytes(dataArr)
        }

        return dataArr
    }

    fun pdf(options: PdfOptions): ByteArray{
        val scale = options.scale
        val displayHeaderFooter = options.displayHeaderFooter
        val headerTemplate = options.headerTemplate ?: ""
        val footerTemplate = options.footerTemplate ?: ""
        val printBackground = options.printBackground
        val landscape = options.landscape
        val pageRanges = options.pageRanges ?: ""

        var paperWidth = 8.5
        var paperHeight = 11.0

        if (options.format != null) {
            val format = PaperFormats[options.format.toLowerCase()]
            paperWidth = format!!.width
            paperHeight = format!!.height
        } else {
            paperWidth = convertPrintParameterToInches(options.width) ?: paperWidth
            paperHeight = convertPrintParameterToInches(options.height) ?: paperHeight
        }

        val marginTop = convertPrintParameterToInches(options.margin?.top) ?: 0.0
        val marginLeft = convertPrintParameterToInches(options.margin?.left) ?: 0.0
        val marginBottom = convertPrintParameterToInches(options.margin?.bottom) ?: 0.0
        val marginRight = convertPrintParameterToInches(options.margin?.right) ?: 0.0

        val preferCSSPageSize = options.preferCSSPageSize

        val requestFrame = session.createRequestFrame("Page.printToPDF")
        requestFrame.params
                .put("landscape", landscape)
                .put("displayHeaderFooter", displayHeaderFooter)
                .put("headerTemplate", headerTemplate)
                .put("footerTemplate", footerTemplate)
                .put("printBackground", printBackground)
                .put("scale", scale)
                .put("paperWidth", paperWidth)
                .put("paperHeight", paperHeight)
                .put("marginTop", marginTop)
                .put("marginBottom", marginBottom)
                .put("marginLeft", marginLeft)
                .put("marginRight", marginRight)
                .put("pageRanges", pageRanges)
                .put("preferCSSPageSize", preferCSSPageSize)
        val (_, result) = session.send(requestFrame).block()

        val data = result!!.get("data").asText()
        var dataArr = Base64.getDecoder().decode(data)

        options.path?.let {
            val image = File(options.path)
            image.parentFile.mkdirs()
            image.writeBytes(dataArr)
        }

        return dataArr
    }


}