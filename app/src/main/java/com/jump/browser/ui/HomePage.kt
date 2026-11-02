package com.jump.browser.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import com.jump.browser.ui.theme.MaterialTheme
import com.jump.browser.ui.theme.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jump.browser.data.Bookmark
import com.jump.browser.ui.theme.JumpAccent
import java.security.MessageDigest

@Composable
fun HomePage(
    bookmarks: List<Bookmark>,
    onSearchClick: () -> Unit,
    onBookmarkClick: (Bookmark) -> Unit,
    onLogoClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    logoPath: String = "",
    backgroundPath: String = "",
    backgroundOpacity: Int = 100,
    searchBarEnabled: Boolean = true,
    favoriteColorEnabled: Boolean = true,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val maxPull = with(density) { 98.dp.toPx() }
    var dragging by remember { mutableStateOf(false) }
    var pull by remember { mutableFloatStateOf(0f) }
    val shownPull by animateFloatAsState(
        targetValue = if (dragging) pull else 0f,
        animationSpec = if (dragging) snap() else tween(140),
        label = "home-pull",
    )
    val backgroundBitmap = remember(backgroundPath) {
        if (backgroundPath.isBlank()) null
        else runCatching { BitmapFactory.decodeFile(backgroundPath)?.asImageBitmap() }.getOrNull()
    }
    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .graphicsLayer {
                translationY = shownPull
                alpha = 1f - (shownPull / maxPull) * 0.9f
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = {
                        dragging = false
                        val accepted = pull > maxPull * 0.9f
                        pull = 0f
                        if (accepted) onSearchClick()
                    },
                    onDragCancel = { dragging = false; pull = 0f },
                    onVerticalDrag = { change, amount ->
                        change.consume()
                        if (amount > 0f || pull > 0f) {
                            pull = (pull + amount).coerceIn(0f, maxPull)
                        }
                    },
                )
            }
    ) {
        if (backgroundBitmap != null) {
            Image(
                bitmap = backgroundBitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = backgroundOpacity / 100f },
                contentScale = ContentScale.Crop,
            )
        }
        Column(
            Modifier
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // #content top:135px (450px viewport) plus the logo intrinsic gap.
            Spacer(Modifier.height(135.dp))
            val logoBitmap = remember(logoPath) {
                if (logoPath.isBlank()) null
                else runCatching { BitmapFactory.decodeFile(logoPath)?.asImageBitmap() }.getOrNull()
            }
            Box(Modifier.clickable { onLogoClick() }) {
                if (logoBitmap != null) {
                    Image(
                        bitmap = logoBitmap,
                        contentDescription = null,
                        modifier = Modifier.size(99.dp, 72.dp).clip(RoundedCornerShape(15.dp)),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    JumpLogo(Modifier.size(99.dp, 72.dp))
                }
            }
            // Inline logo leaves a 9dp descender gap; the field follows a 22dp block gap.
            if (searchBarEnabled) Spacer(Modifier.height(22.dp))
            if (searchBarEnabled) Box(
                Modifier
                    .fillMaxWidth(0.9f)
                    .widthIn(max = 540.dp)
                    .height(46.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Color.Transparent)
                    .clickable { onSearchClick() },
                contentAlignment = Alignment.CenterStart,
            ) {
                val outline = MaterialTheme.colorScheme.outline
                Canvas(Modifier.fillMaxSize()) {
                    drawRoundRect(
                        color = outline,
                        size = size,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(13.dp.toPx()),
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
            }
            if (bookmarks.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                HomeShortcuts(bookmarks.take(18), favoriteColorEnabled, onBookmarkClick)
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun HomeShortcuts(
    bookmarks: List<Bookmark>,
    colorsEnabled: Boolean,
    onClick: (Bookmark) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        bookmarks.chunked(6).forEach { rowItems ->
            Row(
                Modifier.fillMaxWidth(0.9f).widthIn(max = 540.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                rowItems.forEach { bookmark ->
                    FavoriteTile(bookmark, colorsEnabled, onClick)
                    Spacer(Modifier.width(18.dp))
                }
                repeat(6 - rowItems.size) { Spacer(Modifier.width(72.dp)) }
            }
        }
    }
}

@Composable
private fun FavoriteTile(bookmark: Bookmark, colorsEnabled: Boolean, onClick: (Bookmark) -> Unit) {
    val icon = remember(bookmark.iconPath) {
        if (bookmark.iconPath.isBlank()) null
        else runCatching { BitmapFactory.decodeFile(bookmark.iconPath)?.asImageBitmap() }.getOrNull()
    }
    val tileColor = remember(bookmark.url) { favoriteColor(bookmark.url) }
    Column(
        Modifier
            .width(54.dp)
            .clickable { onClick(bookmark) }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(54.dp)
                .background(if (colorsEnabled) tileColor else Color.Transparent, RoundedCornerShape(15.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = null,
                    modifier = Modifier.size(54.dp).clip(RoundedCornerShape(15.dp)),
                    contentScale = ContentScale.Crop,
                )
            } else if (bookmark.title.isNotBlank()) {
                Text(
                    text = bookmark.title.trim().take(1).uppercase(),
                    color = Color.White,
                    fontSize = 15.sp,
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        BasicText(
            text = bookmark.title.ifBlank { bookmark.url },
            style = TextStyle(
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.width(54.dp),
        )
    }
}

/** Same deterministic pastel mapping used by the reference homepage favorites. */
private fun favoriteColor(value: String): Color {
    val digest = runCatching {
        MessageDigest.getInstance("MD5").digest(value.toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }.getOrElse { value.hashCode().toString(16) }
    val r = digest.take(3).toIntOrNull(16)?.rem(128)?.plus(90) ?: 120
    val g = digest.substring(digest.length / 2 - 3, digest.length / 2).toIntOrNull(16)?.rem(128)?.plus(90) ?: 140
    val b = digest.takeLast(3).toIntOrNull(16)?.rem(128)?.plus(90) ?: 180
    return Color(r, g, b)
}

@Composable
fun JumpLogo(modifier: Modifier = Modifier) {
    Canvas(modifier) { drawRibbonLogo() }
}

/**
 * Exact 124x90 geometry from the homepage mark, recreated as a Compose path.
 * Rendered at 99x72dp, the size used by the homepage stylesheet.
 */
private fun DrawScope.drawRibbonLogo() {
    val sx = size.width / 124f
    val sy = size.height / 90f
    fun x(v: Float) = v * sx
    fun y(v: Float) = v * sy

    val blue = Path().apply {
        moveTo(x(21.7384f), y(26.6739f))
        cubicTo(
            x(21.9323f), y(25.0574f),
            x(23.0867f), y(23.7198f),
            x(24.6574f), y(23.2914f),
        )
        lineTo(x(36.033f), y(20.189f))
        cubicTo(
            x(37.3053f), y(19.842f),
            x(38.5592f), y(20.7998f),
            x(38.5592f), y(22.1185f),
        )
        lineTo(x(38.5592f), y(40f))
        lineTo(x(17.5f), y(62f))
        close()
    }
    val orange = Path().apply {
        moveTo(x(67f), y(7.5f))
        lineTo(x(70.5f), y(30.5f))
        lineTo(x(38.7886f), y(68.3644f))
        cubicTo(
            x(37.7137f), y(69.6478f),
            x(35.9532f), y(70.1262f),
            x(34.3766f), y(69.5631f),
        )
        lineTo(x(18.8417f), y(64.0149f))
        cubicTo(
            x(17.4933f), y(63.5333f),
            x(17.0729f), y(61.8324f),
            x(18.0417f), y(60.7782f),
        )
        close()
    }
    val amber = Path().apply {
        moveTo(x(66.7214f), y(8.2943f))
        cubicTo(
            x(66.61f), y(7.64321f),
            x(67.1493f), y(7.06493f),
            x(67.8066f), y(7.13066f),
        )
        lineTo(x(85.1987f), y(8.86987f))
        cubicTo(
            x(85.9892f), y(8.94892f),
            x(86.658f), y(9.4884f),
            x(86.9026f), y(10.2443f),
        )
        lineTo(x(107.813f), y(74.8778f))
        cubicTo(
            x(108.18f), y(76.0106f),
            x(107.482f), y(77.2107f),
            x(106.316f), y(77.4519f),
        )
        lineTo(x(83.555f), y(82.161f))
        cubicTo(
            x(81.3385f), y(82.6196f),
            x(79.1834f), y(81.1494f),
            x(78.8018f), y(78.9184f),
        )
        close()
    }
    drawPath(blue, Color(0xFF00A5F6))
    drawPath(orange, Color(0xFFFF3E00))
    drawPath(amber, Color(0xFFFFB700))
}
