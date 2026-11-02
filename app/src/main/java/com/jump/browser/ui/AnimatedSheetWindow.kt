package com.jump.browser.ui

import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

/**
 * A transparent full-screen window that hosts a bottom sheet.
 *
 * The sheet lives in a real window so it floats above the browser chrome, but
 * it deliberately does **not** use a platform window animation: those follow
 * `window_animation_scale`, which many devices keep at 0.1, so the sheet would
 * appear to jump instead of slide. The window is made transparent here and the
 * sheet runs the reference's own 180ms curve in composition
 * (see [rememberSheetProgress]).
 */
@Composable
fun SheetWindow(
    onDismiss: () -> Unit,
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
        val window = (view.parent as? DialogWindowProvider)?.window
        LaunchedEffect(window) {
            val w = window ?: return@LaunchedEffect
            // Transparent window: the sheet draws its own scrim, and the
            // platform animation must not compress the slide.
            w.setDimAmount(0f)
            w.setWindowAnimations(0)
            w.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
        }
        content()
    }
}
