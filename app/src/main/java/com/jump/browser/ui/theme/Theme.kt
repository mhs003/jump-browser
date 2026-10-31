package com.jump.browser.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

val JumpWhite = Color(0xFFFFFFFF)
val JumpInk = Color(0xFF1C1B1D)
val JumpAccent = Color(0xFF6F8DE1)
val JumpAccentDark = Color(0xFF9AAEEF)
val JumpMuted = Color(0xFF8A8A8E)
val JumpDivider = Color(0xFFE8E8EA)
val JumpSurfaceDark = Color(0xFF232326)
val JumpInkDark = Color(0xFFF3F2F5)
val JumpMutedDark = Color(0xFF9C9CA3)
val JumpDividerDark = Color(0xFF34343A)

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
    onSurface = JumpInk,
    surfaceVariant = Color(0xFFF5F5F7),
    onSurfaceVariant = JumpMuted,
    outline = Color(0xFFD8D8DC),
    outlineVariant = JumpDivider,
    error = Color(0xFFD64146),
)

private val Dark = darkColorScheme(
    primary = JumpAccentDark,
    onPrimary = Color(0xFF122044),
    primaryContainer = Color(0xFF2A3C6E),
    onPrimaryContainer = Color(0xFFDDE4FF),
    secondary = JumpAccentDark,
    onSecondary = Color(0xFF122044),
    background = Color(0xFF1C1B1D),
    onBackground = JumpInkDark,
    surface = JumpSurfaceDark,
    onSurface = JumpInkDark,
    surfaceVariant = Color(0xFF2C2C30),
    onSurfaceVariant = JumpMutedDark,
    outline = Color(0xFF45454C),
    outlineVariant = JumpDividerDark,
    error = Color(0xFFFF7A7E),
)

private val JumpTypography = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 28.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 24.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 15.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 12.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 11.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 10.sp),
)

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
    MaterialTheme(
        colorScheme = scheme,
        typography = JumpTypography,
        content = content,
    )
}
