package com.jump.browser.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.launch
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlin.math.hypot
import kotlin.math.max

/**
 * The app's colour palette.
 *
 * This mirrors the subset of `MaterialTheme` the code was written against
 * (`colorScheme.*`, `typography.*`) so call sites stay unchanged, but it is a
 * plain data holder: the Material3 library is no longer on the classpath,
 * which removes a large amount of unused component code from the release dex.
 */data class JumpColorScheme(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val outlineVariant: Color,
    val error: Color,
)

data class JumpTypography(
    val headlineLarge: TextStyle,
    val headlineMedium: TextStyle,
    val headlineSmall: TextStyle,
    val titleLarge: TextStyle,
    val titleMedium: TextStyle,
    val bodyLarge: TextStyle,
    val bodyMedium: TextStyle,
    val bodySmall: TextStyle,
    val labelLarge: TextStyle,
    val labelMedium: TextStyle,
    val labelSmall: TextStyle,
)

object MaterialTheme {
    val colorScheme: JumpColorScheme
        @Composable @ReadOnlyComposable get() = LocalJumpColorScheme.current

    val typography: JumpTypography
        @Composable @ReadOnlyComposable get() = LocalJumpTypography.current
}

internal val LocalJumpColorScheme = staticCompositionLocalOf { LightColors }
internal val LocalJumpTypography = staticCompositionLocalOf { JumpType }

// Palette extracted from the reference resources.
val JumpWhite = Color(0xFFFFFFFF)
val JumpNight = Color(0xFF1C1B1D)
val JumpAccent = Color(0xFF6F8DE1)
val JumpAccentDark = Color(0xFF617AC1)
val JumpInk = Color(0xDE000000)
val JumpInkDark = Color(0xBEFFFFFF)
val JumpIcon = Color(0xFF232323)
val JumpIconDark = Color(0xFF9F9F9F)
val JumpMuted = Color(0x8A000000)
val JumpMutedDark = Color(0x99FFFFFF)
val JumpOutline = Color(0xFFC2C2C2)
val JumpOutlineDark = Color(0xFF3A3A3D)
val JumpAddressLight = Color(0xFFEEEEEE)
val JumpAddressDark = Color(0xFF242426)
val JumpPressed = Color(0x30808080)
val JumpRipple = Color(0x30808080)
val JumpScrim = Color(0x20000000)

private fun style(size: Int) = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = size.sp,
)

internal val JumpType = JumpTypography(
    headlineLarge = style(28),
    headlineMedium = style(24),
    headlineSmall = style(20),
    titleLarge = style(20),
    titleMedium = style(16),
    bodyLarge = style(16),
    bodyMedium = style(14),
    bodySmall = style(12),
    labelLarge = style(14),
    labelMedium = style(11),
    labelSmall = style(10),
)

internal val LightColors = JumpColorScheme(
    primary = JumpAccent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8EDFC),
    onPrimaryContainer = Color(0xFF1F326E),
    background = JumpWhite,
    onBackground = JumpInk,
    surface = JumpWhite,
    onSurface = JumpIcon,
    surfaceVariant = JumpAddressLight,
    onSurfaceVariant = JumpMuted,
    outline = JumpOutline,
    outlineVariant = Color(0xFFE4E4E4),
    error = Color(0xFFD64146),
)

internal val DarkColors = JumpColorScheme(
    primary = JumpAccentDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2A3C6E),
    onPrimaryContainer = Color(0xFFDDE4FF),
    background = JumpNight,
    onBackground = JumpInkDark,
    surface = JumpNight,
    onSurface = JumpIconDark,
    surfaceVariant = JumpAddressDark,
    onSurfaceVariant = JumpMutedDark,
    outline = JumpOutlineDark,
    outlineVariant = Color(0xFF333336),
    error = Color(0xFFFF7A7E),
)

/**
 * The reference ripple: a translucent grey wash that spreads from the touch
 * point and fades out. Replaces `material3.ripple`, which pulls the whole
 * Material ripple machinery into the APK.
 *
 * Two details make it behave correctly:
 *
 * - The animation is observed as snapshot state and the node invalidates its
 *   draw every frame. Reading an `Animatable` only inside `draw()` does not
 *   schedule repaints, so the ripple would appear once and freeze.
 * - Instances compare equal across recompositions, so Compose reuses the node
 *   instead of tearing it down mid-press (which shows up as a flicker on the
 *   button).
 */
fun ripple(color: Color = JumpRipple, bounded: Boolean = true): IndicationNodeFactory =
    RippleIndication(color, bounded)

/**
 * The two ripple variants used by the app.
 *
 * Hoisted to constants so the same instance is passed on every recomposition.
 * A fresh instance per composition would still compare equal, but keeping one
 * object removes any chance of Compose recreating the node mid-press.
 */
val JumpRippleIndication: IndicationNodeFactory = RippleIndication(JumpRipple, bounded = true)

/** For controls that clip themselves, so the wash is not cut off. */
val JumpRippleUnbounded: IndicationNodeFactory = RippleIndication(JumpRipple, bounded = false)

private class RippleIndication(
    private val color: Color,
    private val bounded: Boolean,
) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        RippleNode(color, bounded, interactionSource)

    override fun equals(other: Any?): Boolean =
        other is RippleIndication && other.color == color && other.bounded == bounded

    override fun hashCode(): Int = 31 * color.hashCode() + bounded.hashCode()
}

private class RippleNode(
    private val color: Color,
    private val bounded: Boolean,
    private val source: InteractionSource,
) : Modifier.Node(), DrawModifierNode {
    /**
     * Plain snapshot state, not an `Animatable`: `DrawModifierNode.draw` is not
     * a snapshot-observed scope, so a value read there does not register a
     * dependency or schedule another frame. Writing this state from the
     * animation loop invalidates the draw on every step.
     */
    private var progress by mutableFloatStateOf(0f)
    private var centre by mutableStateOf(Offset.Unspecified)
    private var job: Job? = null

    override fun onAttach() {
        coroutineScope.launch {
            source.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> {
                        centre = interaction.pressPosition
                        // A new press restarts cleanly instead of fighting the
                        // previous animation.
                        job?.cancel()
                        job = launch { animateProgress(from = 0f, to = 1f, duration = 180) }
                    }
                    is PressInteraction.Release, is PressInteraction.Cancel -> {
                        job?.cancel()
                        val start = progress
                        job = launch { animateProgress(from = start, to = 0f, duration = 160) }
                    }
                }
            }
        }
    }

    /** Steps [progress] in real time so each step invalidates the draw. */
    private suspend fun animateProgress(from: Float, to: Float, duration: Int) {
        val frames = (duration / 16).coerceAtLeast(1)
        for (frame in 0..frames) {
            val t = frame / frames.toFloat()
            progress = from + (to - from) * FastOutSlowInEasing.transform(t)
            delay(16)
        }
        progress = to
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val p = progress
        if (p <= 0.001f) return
        val origin = if (centre == Offset.Unspecified) center else centre
        // A backdrop-wash ripple: the disc grows to cover the whole control
        // from wherever it was pressed. Using the farthest corner (rather than
        // the longer side) is what makes the wash reach every edge.
        val reach = if (bounded) {
            val dx = max(origin.x, size.width - origin.x)
            val dy = max(origin.y, size.height - origin.y)
            hypot(dx, dy)
        } else {
            hypot(size.width, size.height)
        }
        // Grows over the first 60% of the press animation, then holds; the
        // release drives the same value back to zero, which fades the wash.
        val grown = (p / 0.6f).coerceAtMost(1f)
        drawCircle(color = color, radius = reach * grown, center = origin)
    }

}

/**
 * An indication that draws nothing.
 *
 * Used where a control supplies its own feedback. It is a singleton whose
 * equals is stable, so Compose never treats it as "changed" and rebuilds the
 * clickable node underneath — which showed up as buttons flickering on press.
 */
private object NoIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        object : Modifier.Node() {}

    override fun equals(other: Any?): Boolean = other is NoIndication
    override fun hashCode(): Int = 0
}

@Composable
fun JumpTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            if (Build.VERSION.SDK_INT >= 29) {
                window.isNavigationBarContrastEnforced = false
                window.isStatusBarContrastEnforced = false
            }
        }
    }
    CompositionLocalProvider(
        LocalIndication provides NoIndication,
        LocalJumpColorScheme provides scheme,
        LocalJumpTypography provides JumpType,
        content = content,
    )
}
