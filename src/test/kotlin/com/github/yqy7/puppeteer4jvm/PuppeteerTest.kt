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
        val browser = puppeteer.launch(LaunchOptions())
        println("创建browser成功。。。")

        val page = browser.newPage()
        val screenshotOptions = ScreenshotOptions(".temp/a.png")
        page.screenshot(screenshotOptions)

        println("线程结束。。。")

    }
}