package com.github.yqy7.puppeteer4jvm

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.node.ObjectNode

/**
 *  @author qiyun.yqy
 *  @date 2018/10/20
 */
data class RequestFrame(val id: Long, val method: String) {
    var params: ObjectNode = objectNode()

    @JsonIgnore
    var subFrameId: Long? = null

    fun toJson(): String = JsonMapper.writeValueAsString(this)

}