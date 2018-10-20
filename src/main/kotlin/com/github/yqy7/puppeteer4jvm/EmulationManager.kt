package com.github.yqy7.puppeteer4jvm

/**
 *  @author qiyun.yqy
 *  @date 2018/10/20
 */
class EmulationManager(private val session: CDPSession) {
    private var emulatingMobile = false
    private var hasTouch = false

    companion object {
        fun newEmulationManager(session: CDPSession): EmulationManager {
            return EmulationManager(session)
        }
    }

    fun emulateViewport(viewport: Viewport): Boolean {
        val screenOrientation = JsonMapper.createObjectNode()
        if (viewport.isLandscape) {
            screenOrientation.put("angle", 90).put("type", "landscapePrimary")
        } else {
            screenOrientation.put("angle", 0).put("type", "portraitPrimary")
        }

        val requestFrame = session.createRequestFrame("Emulation.setDeviceMetricsOverride")
        requestFrame.params
                .put("mobile", viewport.isMobile)
                .put("width", viewport.width)
                .put("height", viewport.height)
                .put("deviceScaleFactor", viewport.deviceScaleFactor)
                .set("screenOrientation", screenOrientation)
        session.send(requestFrame).block()

        val requestFrame1 = session.createRequestFrame("Emulation.setTouchEmulationEnabled")
        requestFrame1.params.put("enabled", viewport.hasTouch)
        session.send(requestFrame1).block()

        val reloadNeeded = emulatingMobile != viewport.isMobile || hasTouch != viewport.hasTouch
        emulatingMobile = viewport.isMobile
        hasTouch = viewport.hasTouch
        return reloadNeeded
    }
}