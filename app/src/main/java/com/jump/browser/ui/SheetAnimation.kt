package com.jump.browser.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/** Short fade used by every floating surface. */
internal const val FADE_DURATION_MS = 120

/**
 * Fade progress for a floating surface: 0 is transparent, 1 is settled.
 *
 * The surfaces only fade. No slide and no scale: a sheet window is composed
 * only while it is shown, so the first composition starts at 0 and animates
 * to 1; initialising from [visible] would skip the fade entirely.
 */
@Composable
fun rememberSheetProgress(
    visible: Boolean,
    durationMs: Int = FADE_DURATION_MS,
): MutableFloatState {
    val progress = remember { mutableFloatStateOf(0f) }
    val anim = remember { Animatable(0f) }
    LaunchedEffect(progress) {
        anim.animateTo(
            targetValue = if (visible) 1f else 0f,
            animationSpec = tween(durationMs, easing = FastOutSlowInEasing),
        ) {
            progress.floatValue = value
        }
        progress.floatValue = anim.value
    }
    return progress
}

/** Pure opacity, driven by [rememberSheetProgress]. */
fun Modifier.overlayFade(progress: Float): Modifier = graphicsLayer {
    alpha = progress.coerceIn(0f, 1f)
}
