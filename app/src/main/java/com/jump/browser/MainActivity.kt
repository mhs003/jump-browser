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
    private var pendingImageTarget: ((String?) -> Unit)? = null

    private val imagePicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        val callback = pendingImageTarget
        pendingImageTarget = null
        callback?.invoke(uri?.let { copyHomepageImage(it) })
    }

    /** Lets settings pick a custom homepage logo or background. */
    fun pickHomepageImage(onResult: (String?) -> Unit) {
        pendingImageTarget = onResult
        runCatching { imagePicker.launch("image/*") }
            .onFailure {
                pendingImageTarget = null
                onResult(null)
            }
    }

    private fun copyHomepageImage(uri: android.net.Uri): String? = runCatching {
        val dir = java.io.File(filesDir, "homepage").apply { mkdirs() }
        val file = java.io.File(dir, "image-${System.currentTimeMillis()}.png")
        contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        file.absolutePath
    }.getOrNull()

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
            // The chrome theme and "Night mode" are two different things and
            // must not overwrite each other: `darkMode` decides the app's own
            // colours, `nightMode` decides whether web pages are darkened.
            // Merging them (dark = nightMode || darkMode) made the bottom-sheet
            // Night toggle impossible to turn off while Theme was set to Dark.
            val orientationMode = settings.orientation
            LaunchedEffect(orientationMode) {
                requestedOrientation = when (orientationMode) {
                    "portrait" -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    "landscape" -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    "sensor" -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                    else -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }
            val dark = when (settings.darkMode) {
                DarkMode.SYSTEM -> systemDark
                DarkMode.LIGHT -> false
                DarkMode.DARK -> true
            }
            // Toolbars settings → Color mode can force the chrome light or
            // dark independently of the theme and of the page darkening.
            val chromeDark = when (settings.toolbarColor) {
                "DARK" -> true
                "LIGHT" -> false
                else -> dark
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
            JumpTheme(darkTheme = chromeDark) {
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
