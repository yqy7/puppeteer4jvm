package com.github.yqy7.puppeteer4jvm

/**
 *  @author qiyun.yqy
 *  @date 2018/10/19
 */
enum class Platform {
    linux,
    mac,
    win32,
    win64;

    companion object {
        fun current(): Platform {
            val osName = System.getProperty("os.name")
            val osArch = System.getenv("os.arch")
            return when (osName) {
                "Linux" -> linux
                "Mac OS X" -> mac
                "Windows" -> {
                    if ("x86_64".equals(osArch, ignoreCase = true)) {
                        win64
                    } else {
                        win32
                    }
                }
                else -> throw RuntimeException("Unsupported platform: $osName")
            }
        }
    }
}