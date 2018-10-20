package com.github.yqy7.puppeteer4jvm

/**
 *  @author qiyun.yqy
 *  @date 2018/10/19
 */

data class Viewport(val width: Int?,
                    val height: Int?,
                    val deviceScaleFactor: Float = 1f,
                    val isMobile: Boolean = false,
                    val hasTouch: Boolean = false,
                    val isLandscape: Boolean = false)

class Puppeteer(configPath: String?) {
    private val launcher = Launcher(configPath)

    data class ConnectOptions(val browserWSEndpoint: String?,
                              val ignoreHTTPSErrors: Boolean = false,
                              val defaultViewport: Viewport?,
                              val slowMo: Float?)

    fun connect(options: ConnectOptions): Browser {
        throw UnsupportedOperationException()
    }

    data class CreateBrowserFetcherOptions(val host: String?,
                                           val path: String?,
                                           val platform: String?)

    fun createBrowserFetcher(options: CreateBrowserFetcherOptions): BrowserFetcher {
        throw UnsupportedOperationException()
    }

    data class DefaultArgsOptions(val headless: Boolean = true,
                                  val args: List<String>?,
                                  val userDataDir: String?,
                                  val devtools: Boolean = false)

    fun defaultArgs(options: DefaultArgsOptions): List<String> {
        throw UnsupportedOperationException()
    }

    fun executablePath(): String {
        throw UnsupportedOperationException()
    }

    fun launch(options: LaunchOptions): Browser {
        return launcher.launch(options)
    }
}