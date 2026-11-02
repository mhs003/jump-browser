package com.jump.browser.ui

import androidx.compose.animation.core.Easing

/**
 * `@android:anim/bounce_interpolator` (Android's [BounceInterpolator]), as a
 * pure curve so it can be unit-tested off-device. The platform class is a
 * stub in local JVM tests, and this is the exact cubic the platform runs.
 */
class BounceCurveEasing : Easing {
    override fun transform(t: Float): Float {
        val input = (t * 1.1226f) % 1f
        return when {
            input < 0.3535f -> bounce(input)
            input < 0.7408f -> bounce(input - 0.54719f) + 0.7f
            input < 0.9644f -> bounce(input - 0.8526f) + 0.9f
            else -> bounce(input - 1.0435f) + 0.95f
        }
    }

    private fun bounce(t: Float): Float = t * t * 8f
}

/**
 * Motion curves taken from the reference APK resources rather than guessed.
 *
 * - Sheets, tab popups and toasts slide with [SHEET_EASING], which is the
 *   reference `PathInterpolator(0.2f, 0.2f, 0.8f, 0.8f)` used for its 180ms
 *   bottom-sheet, footer and toolbar translations (`x8.h.b()`).
 * - The reference popup window (`@anim/a` / `@anim/b`) animates some
 *   properties with `@android:anim/bounce_interpolator` (the platform
 *   BounceInterpolator, [BounceCurveEasing]) and its alpha with the default
 *   `decelerate` curve:
 *     * enter: alpha 0→1 decelerate; translateY 10%→0 and scale 0.9→1.0
 *       around the bottom-centre pivot with bounce;
 *     * exit: the same properties in reverse.
 *   All of it runs over `@integer/a` = 240ms (see [POPUP_DURATION_MS]).
 * - The 160ms toolbar show/hide uses the platform `accelerate` curve.
 */
object ReferenceMotion {
    /** 180ms — the reference sheet, scrim and toolbar-collapse duration. */
    const val SHEET_DURATION_MS = 180

    /** 240ms — `@integer/a`, the popup/dialog window animation. */
    const val POPUP_DURATION_MS = 240

    /** 160ms — `@integer/d`, toolbar show/hide. */
    const val TOOLBAR_DURATION_MS = 160

    val SHEET_EASING: Easing = PathCurveEasing(0.2f, 0.2f, 0.8f, 0.8f)

    val TOOLBAR_EASING: Easing = PathCurveEasing(0.4f, 0f, 1f, 1f)

    /** The `decelerate` curve (Android's `f(1-(1-t)^2)`), used for popup alpha. */
    val DECELERATE_EASING: Easing = Easing { t -> 1f - (1f - t) * (1f - t) }

    /** Android's `bounce_interpolator`, evaluated exactly as the platform does. */
    val POPUP_IMAGE_EASING: Easing = BounceCurveEasing()
}

/**
 * A `PathInterpolator` equivalent: the same cubic bezier control points and
 * the same x→t solve used by `androidx.core.view.animation.PathInterpolator`.
 */
class PathCurveEasing(
    private val x1: Float,
    private val y1: Float,
    private val x2: Float,
    private val y2: Float,
) : Easing {
    private fun x(t: Float): Float = 3f * x1 * t * (1 - t) * (1 - t) + 3f * x2 * t * t * (1 - t) + t * t * t
    private fun y(t: Float): Float = 3f * y1 * t * (1 - t) * (1 - t) + 3f * y2 * t * t * (1 - t) + t * t * t
    private fun dx(t: Float): Float = 3f * x1 * (1 - t) * (1 - t) + 6f * (x2 - x1) * t * (1 - t) + 3f * (1 - x2) * t * t

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
