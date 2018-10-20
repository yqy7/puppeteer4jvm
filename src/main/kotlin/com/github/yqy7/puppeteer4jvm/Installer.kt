package com.github.yqy7.puppeteer4jvm

import org.slf4j.LoggerFactory

/**
 *  @author qiyun.yqy
 *  @date 2018/10/19
 */
class Installer(configPath: String?) {
    private val log = LoggerFactory.getLogger(this.javaClass)

    private val config: PuppetteerConfig = PuppetteerConfig(configPath)

    fun installChrome() {
        if (config.getBoolean(PUPPETEER_SKIP_CHROMIUM_DOWNLOAD) == true) {
            log.info("Skipping Chromium download.")
            return
        }

        val downloadHost = config.getString(PUPPETEER_DOWNLOAD_HOST)
        val downloadsFolder = config.getString(PUPPETEER_DOWNLOAD_FOLDER)
        val revision = config.getString(PUPPETEER_CHROMIUM_REVISION)

        val browserFetcher = BrowserFetcher(downloadsFolder, downloadHost, revision)
        browserFetcher.download()
    }

}