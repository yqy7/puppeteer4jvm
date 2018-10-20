package com.github.yqy7.puppeteer4jvm

import org.junit.Test

/**
 *  @author qiyun.yqy
 *  @date 2018/10/20
 */

class InstallerTest {

    @Test
    fun testInstallChrome() {
        Installer(null).installChrome()
    }
}