package com.jump.browser

import android.app.Application
import android.content.pm.ApplicationInfo
import android.webkit.WebView
import com.jump.browser.data.AppContainer

class JumpApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            runCatching { WebView.setWebContentsDebuggingEnabled(true) }
        }
    }
}
