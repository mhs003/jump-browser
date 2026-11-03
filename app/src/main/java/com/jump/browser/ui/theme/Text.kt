package com.jump.browser.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.RectangleShape as ShapeRectangle
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape

/**
 * The ambient text style used by [Text] when none is passed, matching the
 * `material3.LocalTextStyle` contract so existing call sites keep working.
 */
val LocalTextStyle = staticCompositionLocalOf { TextStyle.Default }

/**
 * Plain text, drawn with `BasicText`.
 *
 * This replaces `material3.Text`, which resolved its style and colour through
 * the Material typography and content-colour machinery. The app always sets an
 * explicit style for anything where it matters, so a light wrapper is enough.
 */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    style: TextStyle = LocalTextStyle.current,
    fontWeight: androidx.compose.ui.text.font.FontWeight? = null,
    lineHeight: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    letterSpacing: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    fontFamily: androidx.compose.ui.text.font.FontFamily? = null,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE,
    softWrap: Boolean = true,
) {
    val resolved = style.let { base ->
        var s = base
        if (fontSize.isSpecified) s = s.copy(fontSize = fontSize)
        if (lineHeight.isSpecified) s = s.copy(lineHeight = lineHeight)
        if (letterSpacing.isSpecified) s = s.copy(letterSpacing = letterSpacing)
        if (fontFamily != null) s = s.copy(fontFamily = fontFamily)
        if (fontWeight != null) s = s.copy(fontWeight = fontWeight)
        if (color != Color.Unspecified) s = s.copy(color = color)
        if (textAlign != null) s = s.copy(textAlign = textAlign)
        s
    }
    BasicText(
        text = text,
        modifier = modifier,
        style = resolved,
        overflow = overflow,
        maxLines = maxLines,
        softWrap = softWrap,
    )
}

@Composable
fun Text(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = LocalTextStyle.current,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE,
) {
    val resolved = if (color != Color.Unspecified) style.copy(color = color) else style
    BasicText(
        text = text,
        modifier = modifier,
        style = resolved,
        overflow = overflow,
        maxLines = maxLines,
    )
}

/** Draws a vector icon at [size], optionally tinted. */
@Composable
fun Icon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    size: Dp = 24.dp,
) {
    Image(
        painter = rememberVectorPainter(imageVector),
        contentDescription = contentDescription,
        modifier = if (modifier == Modifier) modifier.size(size) else modifier,
        colorFilter = if (tint != Color.Unspecified) ColorFilter.tint(tint) else null,
    )
}

@Composable
fun Icon(
    painter: Painter,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    size: Dp = 24.dp,
) {
    Image(
        painter = painter,
        contentDescription = contentDescription,
        modifier = if (modifier == Modifier) modifier.size(size) else modifier,
        colorFilter = if (tint != Color.Unspecified) ColorFilter.tint(tint) else null,
    )
}

private val androidx.compose.ui.unit.TextUnit.isSpecified: Boolean
    get() = this != androidx.compose.ui.unit.TextUnit.Unspecified

/**
 * Minimal surface: a shape-clipped background with optional elevation.
 *
 * Replaces `material3.Surface`, which brought in the content-colour, tonal
 * elevation and accessibility colour machinery. The app only ever sets a fill,
 * a shape and an elevation, so those are all this accepts.
 */
@Composable
fun Surface(
    modifier: Modifier = Modifier,
    shape: Shape = ShapeRectangle,
    color: Color = MaterialTheme.colorScheme.surface,
    shadowElevation: Dp = 0.dp,
    // Accepted for source compatibility with material3.Surface. The app's
    // design is flat, so tonal elevation never changes the fill.
    @Suppress("UNUSED_PARAMETER") tonalElevation: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .shadow(shadowElevation, shape)
            .clip(shape)
            .background(color),
    ) {
        content()
    }
}

/** Filled button used in empty states and permission prompts. */
@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (enabled) colors.primary else colors.outlineVariant)
            .clickable(
                enabled = enabled,
                indication = JumpPressIndication,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Text-only button, used where a subtle action sits beside a primary one. */
@Composable
fun TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(
                enabled = enabled,
                indication = JumpPressIndication,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Thin determinate progress bar. */
@Composable
fun LinearProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.outlineVariant,
) {
    val fraction = progress().coerceIn(0f, 1f)
    Canvas(modifier) {
        drawRect(trackColor)
        drawRect(color, size = Size(size.width * fraction, size.height))
    }
}

/** Animated on/off switch. */
@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val offset by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(160),
        label = "switch",
    )
    val trackColor = if (checked) colors.primary else colors.outlineVariant
    val interactive = remember { MutableInteractionSource() }
    Box(
        modifier
            .size(width = 42.dp, height = 26.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(trackColor)
            .clickable(
                enabled = enabled,
                indication = JumpPressUnbounded,
                interactionSource = interactive,
            ) { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset { IntOffset((if (checked) 18.dp else 2.dp).roundToPx(), 0) }
                .size(22.dp)
                .background(Color.White, CircleShape),
        )
    }
}
