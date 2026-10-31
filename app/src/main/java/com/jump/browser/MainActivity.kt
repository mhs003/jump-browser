package com.jump.browser

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import org.json.JSONObject
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import com.jump.browser.browser.BrowserController
import com.jump.browser.data.DarkMode
import com.jump.browser.ui.BrowserRoot
import com.jump.browser.ui.theme.JumpTheme

class MainActivity : ComponentActivity() {
    private var controller: BrowserController? = null

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("via_clone_prefs", Context.MODE_PRIVATE)
        val raw = prefs.getString("settings_json", null)
        val mode = runCatching {
            if (raw == null) "LIGHT" else JSONObject(raw).optString("darkMode", "LIGHT")
        }.getOrDefault("LIGHT")
        if (mode == "SYSTEM") {
            super.attachBaseContext(newBase)
            return
        }
        val config = Configuration(newBase.resources.configuration)
        config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
            if (mode == "DARK") Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    private val fileChooser = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        controller?.onFileChooserResult(result.resultCode, result.data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val app = application as JumpApp
        setContent {
            val settings = app.container.settings.settings
            val systemDark = isSystemInDarkTheme()
            val dark = when (settings.darkMode) {
                DarkMode.SYSTEM -> systemDark
                DarkMode.LIGHT -> false
                DarkMode.DARK -> true
            }
            val browser = remember {
                BrowserController(this, app.container) { intent -> fileChooser.launch(intent) }
            }
            SideEffect { controller = browser }
            DisposableEffect(Unit) {
                onDispose { browser.onDestroy() }
            }
            LaunchedEffect(Unit) {
                browser.handleIntent(intent)
            }
            JumpTheme(darkTheme = dark) {
                BrowserRoot(
                    controller = browser,
                    container = app.container,
                    dark = dark,
                    onExit = { finishAndRemoveTask() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        controller?.handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        controller?.onResume()
    }

    override fun onPause() {
        controller?.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        controller?.onDestroy()
        controller = null
        super.onDestroy()
    }
}
