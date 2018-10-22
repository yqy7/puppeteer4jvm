package com.github.yqy7.puppeteer4jvm

import org.junit.Test

/**
 *  @author qiyun.yqy
 *  @date 2018/10/20
 */
class PuppeteerTest {

    @Test
    fun testLaunch() {
        val puppeteer = Puppeteer(null)
        puppeteer.launch(LaunchOptions(headless = true)).use { browser ->
            println("创建browser成功" + browser)
        }
    }
}