package com.github.yqy7.puppeteer4jvm

import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 *  @author qiyun.yqy
 *  @date 2018/10/20
 */
class PageTest {
    val puppeteer = Puppeteer(null)
    lateinit var browser: Browser

    @Before
    fun setUp() {
        browser = puppeteer.launch(LaunchOptions(headless = false))
    }

    @After
    fun tearDown() {
        browser.close()
    }

    @Test
    fun testScreenshot() {
        val page = browser.newPage()
        page.screenshot(ScreenshotOptions(path = "./.temp/a.png"))
    }

    @Test
    fun testPdf() {
        // headless = false不支持pdf
        puppeteer.launch(LaunchOptions(headless = true)).use {
            val page = it.newPage()
            page.pdf(PdfOptions(path = "./.temp/b.pdf"))
        }
    }

    @Test
    fun testGoto() {
        val page = browser.newPage()
        page.goto("http://www.baidu.com", GotoOptions())
        println("goto finished...")
        Thread.sleep(10000)
    }
}