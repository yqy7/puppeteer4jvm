package com.github.yqy7.puppeteer4jvm

import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.net.URLDecoder

/**
 *  @author qiyun.yqy
 *  @date 2018/10/20
 */
internal val JsonMapper = jacksonObjectMapper()

class Multimap<K, V> {
    private val map = mutableMapOf<K, MutableList<V>>()

    fun put(key: K, value: V) {
        if (map.containsKey(key)) {
            map[key]!!.add(value)
        } else {
            map[key] = mutableListOf(value)
        }
    }

    fun get(key: K): List<V>? {
        return map[key]
    }

    fun firstValue(key: K): V? {
        return if (map[key] != null && !map[key]!!.isEmpty()) {
            map[key]!!.first()
        } else {
            null
        }
    }

    fun remove(key: K, value: V) {
        map[key]?.let { it.remove(value) }
    }
}

inline fun <K,V> multimapOf(): Multimap<K, V> = Multimap()

internal inline fun objectNode(obj: Any): ObjectNode = JsonMapper.convertValue(obj, ObjectNode::class.java)
internal inline fun objectNode(): ObjectNode = JsonMapper.createObjectNode()

fun decodeUri(url: String): String = URLDecoder.decode(url, "UTF-8")