package com.github.yqy7.puppeteer4jvm

import com.fasterxml.jackson.databind.node.ObjectNode
import reactor.core.Disposable
import reactor.core.publisher.*
import java.lang.RuntimeException
import java.util.function.Consumer

/**
 *  @author qiyun.yqy
 *  @date 2018/10/20
 */
class FrameManager(
        private val session: CDPSession,
        private val page: Page,
        private val networkManager: NetworkManager
) : EventEmitter() {
    private val defaultNavigationTimeout = 30000
    private val frames = mutableMapOf<String, Frame>()
    private var mainFrame: Frame? = null
    private val contextIdToContext = mutableMapOf<String, ExecutionContext>()

    companion object {
        fun newFrameManager(session: CDPSession, frameTree: ObjectNode, page: Page, networkManager: NetworkManager): FrameManager {
            val frameManager = FrameManager(session, page, networkManager)

            with(frameManager.session) {
                on("Page.frameAttached", Consumer { frameManager.onFrameAttached(it.data!!.get("frameId").asText(), it.data!!.get("parentFrameId")?.asText()) })
                on("Page.frameNavigated", Consumer(frameManager::onFrameNavigated))
                on("Page.navigatedWithinDocument", Consumer(frameManager::onFrameNavigatedWithinDocument))
                on("Page.frameDetached", Consumer(frameManager::onFrameDetached))
                on("Page.frameStoppedLoading", Consumer(frameManager::onFrameStoppedLoading))
                on("Runtime.executionContextCreated", Consumer(frameManager::onExecutionContextCreated))
                on("Runtime.executionContextDestroyed", Consumer(frameManager::onExecutionContextDestroyed))
                on("Runtime.executionContextsCleared", Consumer(frameManager::onExecutionContextsCleared))
                on("Page.lifecycleEvent", Consumer(frameManager::onLifecycleEvent))
            }

            frameManager.handleFrameTree(frameTree)
            return frameManager
        }
    }

    fun mainFrame(): Frame? {
        return mainFrame
    }

    private fun handleFrameTree(frameTree: ObjectNode) {
        frameTree.with("frame").get("parentId")
    }

    private fun onFrameAttached(frameId: String, parentFrameId: String?) {
        if (frames.containsKey(frameId)) {
            return
        }

        val parentFrame = frames.get(parentFrameId)
        val frame = Frame.newFrame(this, session, parentFrame, frameId)
        frames[frame.id] = frame
        emit("frameattached", objectNode(frame))
    }

    private fun onFrameNavigated(event: Event) {
        val framePayload = event.data!!.with("framePayload")
        val frameId = framePayload.get("id").asText()
        val isMainFrame = framePayload.get("parentId") == null
        var frame = if (isMainFrame) mainFrame else frames[frameId]

        // Detach all child frames first.
        if (frame != null) {
            for (childFrame in frame.childFrames()) {
                removeFramesRecursively(childFrame)
            }
        }

        // Update or create main frame.
        if (isMainFrame) {
            if (frame != null) {
                frames.remove(frameId)
                frame.id = frameId
            } else {
                frame = Frame.newFrame(this, session, null, frameId)
            }

            frames[frameId] = frame
            mainFrame = frame
        }

        // Update frame payload.
        frame!!.navigated(framePayload)
        emit("framenavigated", objectNode(frame))
    }

    private fun onFrameNavigatedWithinDocument(event: Event) {

    }

    private fun onFrameDetached(event: Event) {

    }

    private fun onFrameStoppedLoading(event: Event) {

    }

    private fun onExecutionContextCreated(event: Event) {

    }

    private fun onExecutionContextDestroyed(event: Event) {

    }

    private fun onExecutionContextsCleared(event: Event) {

    }

    private fun onLifecycleEvent(event: Event) {

    }

    private fun removeFramesRecursively(frame: Frame) {
        for (childFrame in frame.childFrames()) {
            removeFramesRecursively(childFrame)
        }

        frame.detach()
        frames.remove(frame.id)
        emit("framedetached", objectNode(frame))
    }

    fun navigateFrame(frame: Frame, url: String, options: GotoOptions): Response? {
        val referer = options.referer ?: networkManager.extraHTTPHeaders()["referer"]
        val timeout = options.timeout ?: defaultNavigationTimeout
        val watcher = NavigatorWatcher.newNavigatorWatcher(session, this, networkManager, frame, timeout, options)

        val requestFrame = session.createRequestFrame("Page.navigate")
        requestFrame.params
                .put("url", url)
                .put("referrer", referer)
                .put("frameId", frame.id)
        val (_, result) = session.send(requestFrame).block()
        var ensureNewDocumentNavigation = result?.get("loaderId") != null

        watcher.dispose()

        return watcher.navigationResponse()
    }

}

class Frame private constructor(val frameManager: FrameManager, val session: CDPSession, var parentFrame: Frame?, var id: String) {
    private var url = ""
    private var name: String? = null
    private var navigationURL: String? = null
    private var detached = false
    private var document: ElementHandle? = null
    private var context = ReplayProcessor.create<ExecutionContext>()
    private var childFrames = mutableSetOf<Frame>()
    var loaderId = ""

    init {
        parentFrame?.let { it.childFrames.add(this) }
    }

    companion object {
        fun newFrame(frameManager: FrameManager, session: CDPSession, parentFrame: Frame?, id: String): Frame {
            val frame = Frame(frameManager, session, parentFrame, id)

            return frame
        }
    }

    fun goto(url: String, options: GotoOptions) {
        frameManager.navigateFrame(this, url, options)
    }

    fun setDefaultContext(context: ExecutionContext) {
        this.context.onNext(context)
    }

    fun childFrames(): Set<Frame> {
        return childFrames
    }

    fun detach() {
        // TODO waitTasks

        detached = true
        parentFrame?.let { it.childFrames.remove(this) }
        parentFrame = null
    }

    fun navigated(framePayload: ObjectNode) {
        name = framePayload.get("name").asText()
        navigationURL = framePayload.get("url").asText()
        url = framePayload.get("url").asText()
    }
}

class NavigatorWatcher private constructor(
        val session: CDPSession,
        val frameManager: FrameManager,
        val networkManager: NetworkManager,
        val frame: Frame,
        val timeout: Int?,
        options: GotoOptions
) {
    private val initialLoaderId = frame.loaderId

    private val navigationRequest: Request? = null
    private val hasSameDocumentNavigation = false
    private val disposableList = mutableListOf<Disposable>()

    private val expectedLifecycle: List<String>
    init {
        val waitUntil = if (options.waitUntil.isEmpty()) mutableListOf("load") else options.waitUntil
        expectedLifecycle = waitUntil.map { puppeteerToProtocolLifecycle[it] ?: throw RuntimeException("") }
    }

    companion object {
        fun newNavigatorWatcher(session: CDPSession, frameManager: FrameManager, networkManager: NetworkManager,
                                frame: Frame, timeout: Int?, options: GotoOptions): NavigatorWatcher {
            val navigatorWatcher = NavigatorWatcher(session, frameManager, networkManager, frame, timeout, options)
            return navigatorWatcher
        }
    }

    fun registerEventListeners() {
        // 注册要监听的事件
        disposableList.add(session.connection.on("Connection.Events.Disconnected",
                Consumer<Event> { terminate("Navigation failed because browser has disconnected!")}))
        disposableList.add(session.on("lifecycleevent", Consumer(this::checkLifecycleComplete)))
        disposableList.add(session.on("framenavigatedwithindocument", Consumer(this::navigatedWithinDocument)))
        disposableList.add(session.on("framedetached", Consumer(this::onFrameDetached)))
        disposableList.add(session.on("request", Consumer(this::onRequest)))
    }

    // 检查生命周期是否完成，完成的话执行对应的callback
    fun checkLifecycleComplete(event: Event) {

    }

    // document内跳转
    fun navigatedWithinDocument(event: Event) {

    }

    //
    fun onFrameDetached(event: Event) {

    }

    // 发生请求的时候
    fun onRequest(event: Event) {

    }

    fun terminate(error: String) {

    }

    fun navigationResponse(): Response? {
        return navigationRequest?.response()
    }

    fun dispose() {
        // 取消监听的事件
        for (disposable in disposableList) {
            disposable.dispose()
        }
    }
}

val puppeteerToProtocolLifecycle = mapOf(
        "load" to "load",
        "domcontentloaded" to "DOMContentLoaded",
        "networkidle0" to "networkIdle",
        "networkidle2" to "networkAlmostIdle"
)
