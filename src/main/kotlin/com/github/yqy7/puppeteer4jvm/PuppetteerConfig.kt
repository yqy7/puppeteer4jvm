package com.github.yqy7.puppeteer4jvm

import java.io.File
import java.util.*

/**
 *  @author qiyun.yqy
 *  @date 2018/10/19
 */
class PuppetteerConfig(configPath: String?) {
    companion object {
        private val defaultConfig = Properties()

        init {
            // 默认配置文件
            PuppetteerConfig::class.java.classLoader.getResource("puppeteer_default.properties").openStream().use { defaultConfig.load(it) }
        }

        fun get(key: String): Any? {
            return defaultConfig[key]
        }

        fun getString(key: String): String? {
            return get(key)?.toString()
        }
    }

    private val config = Properties()

    init {
        // 启动时指定的配置文件
        if (System.getProperty("puppetteerConfigPath") != null) {
            File(System.getProperty("puppetteerConfigPath")).inputStream().use { config.load(it) }
        }

        // 调用时指定的配置文件
        if (configPath != null) {
            File(configPath).inputStream().use { config.load(it) }
        }
    }

    /**
     * 可以从-D参数比配置文件优先
     */
    fun get(key: String): Any? {
        return System.getProperty(key) ?: config[key] ?: defaultConfig[key]
    }

    fun getString(key: String): String? {
        return get(key)?.toString()
    }

    fun getBoolean(key: String): Boolean? {
        return get(key)?.let { "true".equals(it.toString(), true) }
    }
}
