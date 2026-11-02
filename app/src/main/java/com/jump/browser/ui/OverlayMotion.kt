package com.jump.browser.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * One animated authority for every floating surface (menu sheet, tab list,
 * title popup, site info, dialogs, toasts).
 *
 * Compose's `AnimatedVisibility` only runs an exit while the composable stays
 * in the tree, and all of these surfaces are switched by a plain boolean. That
 * is why they used to appear and vanish instantly. [OverlayVisibility] keeps
 * the content composed until the leave animation has finished, and exposes two
 * values:
 *
 * - [progress]: 0 = fully open, 1 = fully closed. Motion is "progress → 0"
 *   on open and "progress → 1" on close, the same direction the reference's
 *   window animations use.
 * - [dragOffset]: pixels the user has pulled the surface down (sheets) or
 *   sideways (dialogs) at the dismiss edge, so the gesture and the animation
 *   share one position.
 */
class OverlayVisibility {
    /** What the UI wants right now. */
    var visible by mutableStateOf(false)
        private set

    /** True while shown or still animating out; compose content on this. */
    var present by mutableStateOf(false)
        private set

    internal var hideRequested = false
    internal var restoreRequested = false
    internal val progress = Animatable(1f)

    /** Extra translation from an in-progress dismissal gesture, in pixels. */
    var dragOffset by mutableFloatStateOf(0f)

    fun show() {
        visible = true
        present = true
        hideRequested = false
    }

    fun hide() {
        if (!visible) return
        visible = false
        hideRequested = true
    }

    /** Called when a drag is released before the dismiss threshold. */
    internal fun requestRestore() {
        restoreRequested = true
    }

    internal fun finishExit() {
        present = false
        hideRequested = false
        dragOffset = 0f
    }
}

/**
 * Drives [OverlayVisibility] with the given curve. Opening runs progress 1→0,
 * closing runs 0→1; a gesture that beats the animation snaps from wherever the
 * drag left the surface.
 */
@Composable
fun rememberOverlayMotion(
    visibility: OverlayVisibility,
    durationMs: Int = ReferenceMotion.SHEET_DURATION_MS,
    easing: Easing = ReferenceMotion.SHEET_EASING,
    distancePx: Float = 1f,
): MutableFloatState {
    val state = remember(visibility) { mutableFloatStateOf(1f) }
    LaunchedEffect(visibility.visible, visibility.present, visibility.restoreRequested) {
        if (!visibility.present) {
            visibility.progress.snapTo(1f)
            visibility.dragOffset = 0f
            state.floatValue = 1f
            return@LaunchedEffect
        }
        if (visibility.visible) {
            // A quick double toggle resumes from the current position.
            val from = if (visibility.progress.value >= 1f) 1f else visibility.progress.value
            visibility.dragOffset = 0f
            if (from < 1f) {
                visibility.progress.snapTo(from)
            }
            visibility.progress.animateTo(0f, tween(durationMs, easing = easing))
        } else if (visibility.hideRequested) {
            // If a drag already moved the surface, continue from there with a
            // proportionally shorter animation.
            visibility.progress.animateTo(1f, tween(durationMs, easing = easing)) {
                state.floatValue = visibility.progress.value
                visibility.dragOffset = 0f
            }
            visibility.finishExit()
        }
        state.floatValue = visibility.progress.value
    }
    return state
}

/**
 * Vertical drag-to-dismiss for bottom sheets. [maxDragPx] is the travel at
 * which the sheet counts as dismissed (its own height); release past
 * [threshold] or with enough velocity dismisses, otherwise it snaps back.
 */
@Composable
fun Modifier.sheetDragToDismiss(
    visibility: OverlayVisibility,
    maxDragPx: Float,
    threshold: Float = 0.46f,
): Modifier = draggable(
    orientation = Orientation.Vertical,
    state = rememberDraggableState { delta ->
        if (!visibility.visible) return@rememberDraggableState
        val next = (visibility.dragOffset + delta).coerceAtLeast(0f)
        visibility.dragOffset = next.coerceAtMost(maxDragPx)
    },
    onDragStopped = { velocity ->
        val past = visibility.dragOffset / maxDragPx >= threshold || velocity > 900f
        if (past) {
            visibility.hide()
        } else {
            visibility.dragOffset = 0f
        }
    },
)

/**
 * Horizontal drag-to-dismiss for dialogs and side drawers. Positive [delta]
 * moves the surface towards the right edge it was dismissed from.
 */
@Composable
fun Modifier.overlayHorizontalDragToDismiss(
    visibility: OverlayVisibility,
    maxDragPx: Float,
    threshold: Float = 0.46f,
): Modifier = draggable(
    orientation = Orientation.Horizontal,
    state = rememberDraggableState { delta ->
        if (!visibility.visible) return@rememberDraggableState
        val next = (visibility.dragOffset + delta).coerceAtLeast(0f)
        visibility.dragOffset = next.coerceAtMost(maxDragPx)
    },
    onDragStopped = { velocity ->
        val past = visibility.dragOffset / maxDragPx >= threshold || velocity > 1200f
        if (past) {
            visibility.hide()
        } else {
            visibility.dragOffset = 0f
        }
    },
)

/** Bottom-anchored slide. progress 1 parks the surface [distancePx] lower. */
fun Modifier.overlaySlideUp(progress: Float, distancePx: Float): Modifier =
    graphicsLayer {
        translationY = progress * distancePx
    }

/**
 * Window-style scale/rise used by the popup and site-info surfaces: the
 * surface rises 10% of its height and scales 0.9→1.0 around the bottom edge
 * following [imageEasing]; the caller supplies the alpha track because the
 * reference animates alpha with a different curve.
 */
fun Modifier.overlayPopupScale(
    progress: Float,
    imageEasing: Easing,
    heightPx: Float,
    alpha: Float,
): Modifier {
    val image = imageEasing.transform(progress.coerceIn(0f, 1f))
    return graphicsLayer {
        this.alpha = alpha
        translationY = progress.coerceIn(0f, 1f) * heightPx * 0.1f
        scaleX = 1f - 0.1f * image
        scaleY = 1f - 0.1f * image
        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
    }
}
