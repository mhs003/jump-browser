package com.jump.browser.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

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
val JumpScrim = Color(0x2E000000)

private val Light = lightColorScheme(
    primary = JumpAccent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8EDFC),
    onPrimaryContainer = Color(0xFF1F326E),
    secondary = JumpAccent,
    onSecondary = Color.White,
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

private val Dark = darkColorScheme(
    primary = JumpAccentDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2A3C6E),
    onPrimaryContainer = Color(0xFFDDE4FF),
    secondary = JumpAccentDark,
    onSecondary = Color.White,
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

private val JumpTypography = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 28.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 24.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 20.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 12.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 11.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 10.sp),
)

private object NoIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = object : Modifier.Node() {}
    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = 0
}

@Composable
fun JumpTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) Dark else Light
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
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = JumpTypography,
            content = content,
        )
    }
}
