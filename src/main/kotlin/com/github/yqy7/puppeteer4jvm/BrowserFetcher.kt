package com.github.yqy7.puppeteer4jvm

import org.slf4j.LoggerFactory
import java.io.File
import java.net.URL

/**
 *  @author qiyun.yqy
 *  @date 2018/10/19
 */

val DEFAULT_DOWNLOAD_HOST = "https://storage.googleapis.com"
val DOWNLOAD_URLS = mapOf(
        Platform.linux to "%s/chromium-browser-snapshots/Linux_x64/%s/%s.zip",
        Platform.mac to "%s/chromium-browser-snapshots/Mac/%s/%s.zip",
        Platform.win32 to "%s/chromium-browser-snapshots/Win/%s/%s.zip",
        Platform.win64 to "%s/chromium-browser-snapshots/Win_x64/%s/%s.zip")

class BrowserFetcher(downloadsFolder: String? = null, downloadHost: String? = null, revision: String? = null, platform: String? = null) {
    private val log = LoggerFactory.getLogger(this.javaClass)
    private val downloadsFolder: File = downloadsFolder?.let { File(downloadsFolder) }
            ?: File(System.getProperty("user.dir")).resolve(".local-chromium")
    private val downloadHost: String = downloadHost ?: DEFAULT_DOWNLOAD_HOST
    private val revision: String = revision ?: PuppetteerConfig.getString(PUPPETEER_CHROMIUM_REVISION)!!
    private val platform: Platform = platform?.let { Platform.valueOf(platform) } ?: Platform.current()

    fun canDownload(revision: String): Boolean {
        throw UnsupportedOperationException()
    }

    data class DownloadInfo(val revision: String?,
                            val folderPath: String?,
                            val executablePath: String?,
                            val url: String?,
                            val local: Boolean?)

    fun download(revision: String? = null): DownloadInfo {
        val downloadRevision = revision ?: this.revision
        val downloadURL = downloadUrl(downloadRevision)
        val downloadFolder = revisionFolder(downloadRevision)
        downloadFolder.mkdirs()
        val zipFile = downloadFolder.resolve("download-$platform-$downloadRevision.zip")
        if (zipFile.exists()) {
            log.info("Chromium has downloaded: ${zipFile.absolutePath}")
        } else {
            // 下载
            downloadFile(downloadURL, zipFile)
            // 解压
            extractZip(zipFile)
            log.info("Chromium download success: ${zipFile.absolutePath}")
        }

        val executablePath = resolveExecutablePath(downloadRevision)
        return DownloadInfo(downloadRevision, downloadFolder.absolutePath, executablePath, downloadURL, true)
    }

    private fun downloadUrl(revision: String): String {
        return String.format(DOWNLOAD_URLS[platform]!!, downloadHost, revision, archiveName())
    }

    private fun archiveName(): String {
        return when (platform) {
            Platform.mac -> "chrome-mac"
            Platform.linux -> "chrome-linux"
            // win32 || win64, Windows archive name changed at r591479
            else -> if (revision.toInt() > 591479) "chrome-win" else "chrome-win32"
        }
    }

    private fun revisionFolder(revision: String?): File {
        val resolveRevision = revision ?: this.revision
        return downloadsFolder.resolve("$platform-$resolveRevision")
    }

    private fun downloadFile(url: String, zipFile: File) {
        URL(url).openStream().use { urlIs -> zipFile.outputStream().use { fileOs -> urlIs.copyTo(fileOs) } }
    }

    private fun extractZip(zipFile: File) {
        ProcessBuilder().command("unzip", "-d", zipFile.parent.toString(), zipFile.toString())
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .start()
                .waitFor()
    }

    internal fun resolveExecutablePath(revision: String? = null): String {
        return when (platform) {
            Platform.mac -> revisionFolder(revision).resolve(archiveName())
                    .resolve("Chromium.app")
                    .resolve("Contents")
                    .resolve("MacOS")
                    .resolve("Chromium").absolutePath
            Platform.linux -> revisionFolder(revision)
                    .resolve(archiveName())
                    .resolve("chrome").absolutePath
            else -> revisionFolder(revision)
                    .resolve(archiveName())
                    .resolve("chrome.exe").absolutePath
        }
    }

    fun localRevisions(): List<String> {
        throw UnsupportedOperationException()
    }

    fun platform(): String {
        return platform.name
    }

    data class RevisionInfo(val revision: String? = null,
                            val folderPath: String? = null,
                            val executablePath: String? = null,
                            val url: String? = null,
                            val local: Boolean? = null)

    fun revisionInfo(): RevisionInfo {
        throw UnsupportedOperationException()
    }
}