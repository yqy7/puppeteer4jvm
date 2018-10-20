package com.github.yqy7.puppeteer4jvm

import java.util.function.Consumer

/**
 *  @author qiyun.yqy
 *  @date 2018/10/20
 */

fun main(args: Array<String>) {
    val a = A()
    a.on("abc", Consumer(::hh))
}

class A {
    fun on(event: String, consumer: Consumer<String>) {
        consumer.accept(event)
    }

    fun hh(e: String) {
        println(e)
        println(e)
        println(e)
    }

}

fun hh(e: String) {
    println(e)
    println(e)
}