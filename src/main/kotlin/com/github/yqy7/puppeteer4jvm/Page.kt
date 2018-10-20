package com.github.yqy7.puppeteer4jvm

import com.fasterxml.jackson.databind.node.ObjectNode
import java.io.File
import java.io.IOException
import java.nio.file.Files
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

class Page(private val session: CDPSession, private val target: Target, private val frameTree: ObjectNode) : EventEmitter() {
    private val networkManager = NetworkManager.newNetworkManager(session)
    private val frameManager = FrameManager.newFrameManager()
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

    fun screenshot(options: ScreenshotOptions): String {
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

            val screenOrientation = JsonMapper.createObjectNode()
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
            requestFrame.params.set("color", JsonMapper.convertValue(Color(0, 0, 0, 0), ObjectNode::class.java))
            session.send(requestFrame).block()
        }

        val requestFrame = session.createRequestFrame("Page.captureScreenshot")
        requestFrame.params
                .put("format", "png")
                .put("quality", options.quality)

        clip?.let { requestFrame.params.set("clip", JsonMapper.convertValue(clip, ObjectNode::class.java)) }

        val (_, result) = session.send(requestFrame).block()

        if (shouldSetDefaultBackground) {
            session.send(session.createRequestFrame("Emulation.setDefaultBackgroundColorOverride")).block()
        }

        if (options.fullPage) {
            viewport?.let { setViewport(it) }
        }

        val data = result!!.get("data").asText()

        options.path?.let {
            val dataArr = Base64.getDecoder().decode(data)
            val image = File(options.path)
            image.parentFile.mkdirs()
            image.writeBytes(dataArr)
        }

        return data
    }
}