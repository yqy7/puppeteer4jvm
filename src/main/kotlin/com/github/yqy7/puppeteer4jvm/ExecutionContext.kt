package com.github.yqy7.puppeteer4jvm

import com.fasterxml.jackson.databind.node.ObjectNode


/**
 *  @author qiyun.yqy
 *  @date 2018/10/22
 */

const val EVALUATION_SCRIPT_URL = "__puppeteer_evaluation_script__"
val SOURCE_URL_REGEX = Regex("^[\\040\\t]*\\/\\/[@#] sourceURL=\\s*(\\S*?)\\s*\$", RegexOption.MULTILINE)

fun createJSHandle() {

}

class ExecutionContext(
        val session: CDPSession,
        val contextPayload: ObjectNode,
        val frame: Frame
) {
    private val contextId: String = contextPayload.get("id").asText()
    private val isDefault: Boolean = contextPayload.get("auxData")?.asBoolean() ?: false

    fun frame(): Frame {
        return frame
    }

    fun evaluate() {

    }

    fun evaluateHandle() {

    }

    fun queryObjects() {

    }
}

open class JSHandle(
        protected val context: ExecutionContext,
        protected val session: CDPSession,
        protected val remoteObject: ObjectNode
) {
    protected val disposed = false

    fun executionContext(): ExecutionContext {
        return context
    }

    fun getProperty() {

    }

    fun getProperties() {

    }

    fun jsonValue() {

    }

    open fun asElement(): ElementHandle? {
        return null
    }

    fun dispose() {

    }

    override fun toString(): String {
        return super.toString()
    }
}

class ElementHandle(
        context: ExecutionContext,
        session: CDPSession,
        remoteObject: ObjectNode,
        val page: Page,
        val frameManage: FrameManager
) : JSHandle(context, session, remoteObject) {

    override fun asElement(): ElementHandle? {
        return this
    }

    fun contentFrame() {

    }

    fun scrollIntoViewIfNeeded() {

    }

    fun clickablePoint() {

    }

    fun getBoxModel() {

    }

    fun fromProtocolQuad() {

    }

    fun hover() {

    }

    fun click() {

    }

    fun uploadFile() {

    }

    fun tap() {

    }

    fun focus() {

    }

    fun type() {

    }

    fun press() {

    }

    fun boundingBox() {

    }

    fun boxModel() {

    }

    fun screenshot() {

    }

    fun `$`() {

    }

    fun `$$`() {

    }

    fun `$eval`() {

    }

    fun `$$eval`() {

    }

    fun `$x`() {

    }

    fun isIntersectingViewport() {

    }


}