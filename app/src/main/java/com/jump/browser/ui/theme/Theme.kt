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
 * The press feedback used everywhere: a flat grey wash over the control while
 * it is held, cleared on release.
 *
 * This is deliberately not an animated ripple. A ripple needs frame-accurate
 * expansion, fade timing and clipping to look right, and anything slightly off
 * reads as a flicker or a blob that stops short of the edge. A static tint is
 * what the reference shows on a held control, it cannot look wrong, and it
 * costs one layer instead of a per-frame animation.
 */
val JumpPressIndication: IndicationNodeFactory = PressIndication(JumpPressed)

/** Same feedback for controls that clip themselves. */
val JumpPressUnbounded: IndicationNodeFactory = PressIndication(JumpPressed)

private class PressIndication(
    private val color: Color,
) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        PressNode(color, interactionSource)

    override fun equals(other: Any?): Boolean =
        other is PressIndication && other.color == color

    override fun hashCode(): Int = color.hashCode()
}

private class PressNode(
    private val color: Color,
    private val source: InteractionSource,
) : Modifier.Node(), DrawModifierNode {
    private var pressed by mutableStateOf(false)

    override fun onAttach() {
        coroutineScope.launch {
            source.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> pressed = true
                    is PressInteraction.Release, is PressInteraction.Cancel -> pressed = false
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (pressed) drawRect(color)
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
