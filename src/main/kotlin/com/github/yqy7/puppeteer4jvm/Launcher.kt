package com.github.yqy7.puppeteer4jvm

import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.util.stream.Collectors

/**
 *  @author qiyun.yqy
 *  @date 2018/10/19
 */

private val wsPattern = "^DevTools listening on (ws:\\/\\/.*)$".toPattern()
private val CHROME_PROFILE_PATH = "puppeteer_dev_profile-"

private val DEFAULT_ARGS = setOf(
        "--disable-background-networking",
        "--disable-background-timer-throttling",
        "--disable-breakpad",
        "--disable-client-side-phishing-detection",
        "--disable-default-apps",
        "--disable-dev-shm-usage",
        "--disable-extensions",
        "--disable-features=site-per-process",
        "--disable-hang-monitor",
        "--disable-popup-blocking",
        "--disable-prompt-on-repost",
        "--disable-sync",
        "--disable-translate",
        "--metrics-recording-only",
        "--no-first-run",
        "--safebrowsing-disable-auto-update",
        "--enable-automation",
        "--password-store=basic",
        "--use-mock-keychain"
)

data class LaunchOptions(val ignoreHTTPSErrors: Boolean = false,
                         val headless: Boolean = true,
                         val executablePath: String? = null,
                         val slowMo: Float? = null,
                         val defaultViewport: Viewport? = null,
                         val args: List<String> = listOf(),
                         val ignoreDefaultArgs: List<String> = listOf(),
                         val handleSIGINT: Boolean = true,
                         val handleSIGTERM: Boolean = true,
                         val handleSIGHUP: Boolean = true,
                         val timeout: Int = 30000,
                         val dumpio: Boolean = false,
                         val userDataDir: String? = null,
                         val env: Any? = null,
                         val devtools: Boolean = false,
                         val pipe: Boolean = false)

class Launcher(configPath: String?) {
    private val log = LoggerFactory.getLogger(this.javaClass)
    private val config = PuppetteerConfig(configPath)

    fun launch(options: LaunchOptions): Browser {
        val chromeArguments = mutableSetOf<String>()
        if (options.args.isEmpty() && options.ignoreDefaultArgs.isEmpty()) {
            chromeArguments.addAll(defaultArgs(options))
        } else if (options.args.isEmpty() && !options.ignoreDefaultArgs.isEmpty()) {
            chromeArguments.addAll(defaultArgs(options).stream()
                    .filter { argument -> !options.ignoreDefaultArgs.contains(argument) }.collect(Collectors.toSet()))
        } else {
            chromeArguments.addAll(options.args)
        }

        if (chromeArguments.stream().noneMatch { argument -> argument.startsWith("--remote-debugging-") }) {
            chromeArguments.add(if (options.pipe) "--remote-debugging-pipe" else "--remote-debugging-port=0")
        }

        if (chromeArguments.stream().noneMatch { argument -> argument.startsWith("--user-data-dir") }) {
            val temporaryUserDataDir = Files.createTempDirectory(CHROME_PROFILE_PATH)
            chromeArguments.add("--user-data-dir=$temporaryUserDataDir")
        }

        val chromeExecutable = options.executablePath ?: resolveExecutablePath()
        val executeArgs = mutableListOf<String>()
        executeArgs.add(chromeExecutable)
        executeArgs.addAll(chromeArguments)

        val process = ProcessBuilder().command(executeArgs).start()
        val browserWSEndpoint = findWsUrl(options.timeout, process)

        log.info("获取到的websocket连接: $browserWSEndpoint")

        val connection = Connection.newConnection(process, browserWSEndpoint)
        val browser = Browser.newBrowser(connection, options.ignoreHTTPSErrors,
                options.defaultViewport, null)

        return browser
    }

    private fun defaultArgs(options: LaunchOptions): Set<String> {
        val chromeArguments = mutableSetOf<String>()
        if (options.userDataDir != null) {
            chromeArguments.add("--user-data-dir=" + options.userDataDir)
        }

        if (options.devtools) {
            chromeArguments.add("--auto-open-devtools-for-tabs")
        }

        if (options.headless) {
            chromeArguments.add("--headless")
            chromeArguments.add("--hide-scrollbars")
            chromeArguments.add("--mute-audio")
            if (Platform.current() === Platform.win32) {
                chromeArguments.add("--disable-gpu")
            }
        }

        if (options.args.stream().allMatch { argument -> argument.startsWith("-") }) {
            chromeArguments.add("about:blank")
        }

        chromeArguments.addAll(options.args)
        return chromeArguments
    }

    private fun resolveExecutablePath(): String {
        if (System.getProperty("PUPPETEER_EXECUTABLE_PATH") != null) {
            return System.getProperty("PUPPETEER_EXECUTABLE_PATH")
        }

        val downloadHost = config.getString(PUPPETEER_DOWNLOAD_HOST)
        val downloadsFolder = config.getString(PUPPETEER_DOWNLOAD_FOLDER)
        val revision = config.getString(PUPPETEER_CHROMIUM_REVISION)

        val browserFetcher = BrowserFetcher(downloadsFolder, downloadHost, revision)
        return browserFetcher.resolveExecutablePath()
    }

    private fun findWsUrl(timeout: Int, chromeProcess: Process): String {
        chromeProcess.errorStream.bufferedReader().use {
            val start = System.currentTimeMillis()
            while (System.currentTimeMillis() - start <= timeout) {
                val line = it.readLine()
                if (line != null) {
                    val matcher = wsPattern.matcher(line)
                    if (matcher.find()) {
                        return matcher.group(1)
                    }
                }
            }
        }

        throw RuntimeException("获取websocket连接串超时！")
    }

}