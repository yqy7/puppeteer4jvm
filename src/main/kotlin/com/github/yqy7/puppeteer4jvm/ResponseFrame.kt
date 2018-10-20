package com.github.yqy7.puppeteer4jvm

import com.fasterxml.jackson.databind.node.ObjectNode

/**
 *  @author qiyun.yqy
 *  @date 2018/10/20
 */
data class Error(
        val code: Long,
        val message: String,
        val data: String?
)

data class ResponseFrame(
        val id: Long?,
        val result: ObjectNode?,
        val error: Error?,
        val method: String?,
        val params: ObjectNode?
) {
    fun isEvent(): Boolean = this.method != null
    fun isResponse(): Boolean = !this.isEvent()
    fun isResponse(requestId: Long?): Boolean = isResponse() && id == requestId
    fun isEvent(eventName: String?) = isEvent() && method == eventName
}