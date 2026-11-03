package com.jump.browser.ui

import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

/**
 * A transparent window that hosts a floating sheet over the page area.
 *
 * The sheet lives in a real window so it floats above the browser chrome. The
 * window does not animate: platform window animations follow
 * `window_animation_scale`, which many devices keep near zero, so the sheet
 * only fades, driven in composition.
 *
 * The window is sized to the page area — between the address bar and the
 * bottom bar — rather than the whole screen, and marked
 * `FLAG_NOT_TOUCH_MODAL`. A full-screen window claimed every touch, which is
 * why the address bar and bottom bar stopped responding while the tab list was
 * open; with the window bounded, taps on the chrome reach the browser.
 */
@Composable
fun SheetWindow(
    onDismiss: () -> Unit,
    contentTopPx: Int = 0,
    contentBottomPx: Int = 0,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        val view = LocalView.current
        // The ComposeView's parent is the dialog decor's content view, not the
        // DialogWindowProvider, so walk up until the provider is found.
        val window = generateSequence(view.parent) { it.parent }
            .filterIsInstance<DialogWindowProvider>()
            .firstOrNull()
            ?.window
        LaunchedEffect(window, view, contentTopPx, contentBottomPx) {
            val w = window ?: return@LaunchedEffect
            // Transparent window: the sheet draws its own fade.
            w.setDimAmount(0f)
            w.setWindowAnimations(0)
            w.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
            // Touches outside this window must fall through to the browser.
            w.addFlags(
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            )
            w.decorView.post {
                val screenHeight = w.decorView.height
                if (screenHeight <= 0) return@post
                // Resizing the window made the system stop painting the
                // activity's status-bar colour, leaving a white band. The
                // dialog window covers it again, so colour it to match the
                // chrome instead of leaving the default.
                runCatching {
                    w.statusBarColor = (view.context as? android.app.Activity)
                        ?.window?.statusBarColor ?: AndroidColor.TRANSPARENT
                }
                val attrs = w.attributes
                // Anchor the window to the strip between the chrome bars.
                attrs.gravity = Gravity.TOP or Gravity.START
                attrs.width = WindowManager.LayoutParams.MATCH_PARENT
                attrs.height = (screenHeight - contentTopPx - contentBottomPx)
                    .coerceAtLeast(0)
                attrs.y = contentTopPx
                // Resizing the window makes the system treat it as drawing in
                // the status-bar area, which painted a white bar behind it.
                attrs.flags = attrs.flags or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                w.attributes = attrs
            }
        }
        content()
    }
}
