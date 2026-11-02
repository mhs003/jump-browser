package com.jump.browser.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/** The reference sheet curve: `PathInterpolator(0.2, 0.2, 0.8, 0.8)`. */
internal val ReferenceSheetEasing: Easing = PathEasing(0.2f, 0.2f, 0.8f, 0.8f)

internal const val SHEET_DURATION_MS = 180

/**
 * Open progress for a sheet: 0 is closed, 1 is fully open.
 *
 * The reference slides its measured panel over 180ms with a property
 * animation, which follows `animator_duration_scale` but ignores
 * `window_animation_scale`. Driving the same 180ms curve here reproduces that
 * timing on every device, including ones where window animations are scaled
 * down — where the window-animation approach both started late and finished
 * too fast.
 */
@Composable
fun rememberSheetProgress(
    visible: Boolean,
    durationMs: Int = SHEET_DURATION_MS,
): MutableFloatState {
    // The sheet window is only composed while it is visible, so the very
    // first composition must start closed and animate open. Starting at the
    // current visibility would skip the entrance entirely.
    val progress = remember { mutableFloatStateOf(0f) }
    val anim = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        anim.animateTo(
            targetValue = if (visible) 1f else 0f,
            animationSpec = tween(durationMs, easing = ReferenceSheetEasing),
        ) {
            progress.floatValue = value
        }
        progress.floatValue = anim.value
    }
    return progress
}

/**
 * Translates the sheet up from its own height. [heightPx] is the measured
 * sheet height, matching the reference which translates by the panel's
 * `getHeight()`.
 */
fun Modifier.sheetSlide(progress: Float, heightPx: Float): Modifier = graphicsLayer {
    translationY = (1f - progress) * heightPx
}

/**
 * Android's `PathInterpolator`, evaluated the way `androidx.core` does, so the
 * curve matches the reference bit for bit instead of an approximation.
 */
internal class PathEasing(
    private val x1: Float,
    private val y1: Float,
    private val x2: Float,
    private val y2: Float,
) : Easing {
    private fun x(t: Float) = 3 * x1 * t * (1 - t) * (1 - t) + 3 * x2 * t * t * (1 - t) + t * t * t
    private fun y(t: Float) = 3 * y1 * t * (1 - t) * (1 - t) + 3 * y2 * t * t * (1 - t) + t * t * t
    private fun dx(t: Float) = 3 * x1 * (1 - t) * (1 - t) + 6 * (x2 - x1) * t * (1 - t) + 3 * (1 - x2) * t * t

    override fun transform(fraction: Float): Float {
        if (fraction <= 0f) return 0f
        if (fraction >= 1f) return 1f
        var t = fraction
        repeat(8) {
            val err = x(t) - fraction
            if (kotlin.math.abs(err) < 1e-6f) return y(t)
            val d = dx(t)
            if (kotlin.math.abs(d) < 1e-6f) return y(t)
            t -= err / d
        }
        var lo = 0f
        var hi = 1f
        t = fraction
        repeat(24) {
            if (x(t) < fraction) lo = t else hi = t
            t = (lo + hi) / 2f
        }
        return y(t)
    }
}
