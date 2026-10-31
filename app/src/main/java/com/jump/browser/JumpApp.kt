package com.jump.browser

import android.app.Application
import android.webkit.WebView
import com.jump.browser.data.AppContainer

class JumpApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        runCatching { WebView.setWebContentsDebuggingEnabled(true) }
    }
}
