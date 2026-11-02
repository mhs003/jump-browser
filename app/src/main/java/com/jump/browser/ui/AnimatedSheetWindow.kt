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
 * The sheet lives in a real window so it floats above the browser chrome. The
 * window itself does not animate: platform window animations follow
 * `window_animation_scale`, which many devices keep near zero. The sheet only
 * fades, driven in composition by [rememberSheetProgress].
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
            // Transparent window: the sheet draws its own scrim and fade.
            w.setDimAmount(0f)
            w.setWindowAnimations(0)
            w.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
        }
        content()
    }
}
