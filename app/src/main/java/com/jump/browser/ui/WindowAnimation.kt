package com.jump.browser.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import com.jump.browser.R

/**
 * The floating surfaces are real windows, so they animate the way the
 * reference's windows do — with a platform window animation on the dialog,
 * not with Compose enter/exit transitions that get cut off the moment the
 * owning composable leaves the tree.
 *
 * [style] is a window-animation style from `res/values/styles.xml`; each
 * style names the enter and exit animation from `res/anim`.
 */
enum class JumpWindowAnimation(val style: Int) {
    /** Plain fade used by dialogs. */
    POPUP(R.style.Animation_Jump_Popup),

    /** A plain fade for scrim layers. */
    SCRIM(R.style.Animation_Jump_Scrim),

    /** No window animation: the surface animates itself in composition. */
    NONE(0),
}

/**
 * Installs [animation] on the host dialog window and turns off the platform
 * dim so each surface can own its scrim without the two doubling up.
 */
@Composable
fun JumpDialogWindow(animation: JumpWindowAnimation, dimAmount: Float = 0f) {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    LaunchedEffect(window, animation) {
        if (window == null) return@LaunchedEffect
        window.setDimAmount(dimAmount)
        window.setWindowAnimations(animation.style)
    }
}
