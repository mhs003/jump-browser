package com.jump.browser.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jump.browser.data.Bookmark
import com.jump.browser.ui.theme.JumpAccent

@Composable
fun HomePage(
    bookmarks: List<Bookmark>,
    onSearchClick: () -> Unit,
    onBookmarkClick: (Bookmark) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(126.dp))
            JumpLogo(Modifier.size(108.dp, 86.dp))
            Spacer(Modifier.height(2.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .height(48.dp)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(24.dp),
                    )
                    .clickable { onSearchClick() },
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = "",
                    modifier = Modifier.padding(horizontal = 20.dp),
                    fontSize = 15.sp,
                )
            }
            if (bookmarks.isNotEmpty()) {
                Spacer(Modifier.height(30.dp))
                HomeShortcuts(bookmarks.take(8), onBookmarkClick)
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun HomeShortcuts(bookmarks: List<Bookmark>, onClick: (Bookmark) -> Unit) {
    Column(
        Modifier.fillMaxWidth().widthIn(max = 440.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        bookmarks.chunked(4).forEach { rowItems ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                rowItems.forEach { bookmark ->
                    Column(
                        Modifier
                            .weight(1f)
                            .clickable { onClick(bookmark) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val icon = remember(bookmark.iconPath) {
                            if (bookmark.iconPath.isBlank()) null
                            else runCatching { BitmapFactory.decodeFile(bookmark.iconPath)?.asImageBitmap() }.getOrNull()
                        }
                        Box(
                            Modifier
                                .size(44.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (icon != null) {
                                Image(
                                    bitmap = icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(44.dp).clip(CircleShape),
                                    contentScale = ContentScale.Crop,
                                )
                            } else {
                                Text(
                                    text = bookmark.title.trim().take(1).uppercase().ifBlank { "•" },
                                    color = JumpAccent,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = bookmark.title.ifBlank { bookmark.url },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                repeat(4 - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun JumpLogo(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRibbonLogo()
    }
}

private fun DrawScope.drawRibbonLogo() {
    val w = size.width
    val h = size.height
    fun p(x: Float, y: Float) = Offset(x * w, y * h)

    val blue = Path().apply {
        moveTo(0.16f * w, 0.37f * h)
        lineTo(0.27f * w, 0.31f * h)
        lineTo(0.27f * w, 0.61f * h)
        lineTo(0.16f * w, 0.70f * h)
        close()
    }
    val orange = Path().apply {
        moveTo(0.12f * w, 0.70f * h)
        lineTo(0.26f * w, 0.78f * h)
        lineTo(0.62f * w, 0.14f * h)
        lineTo(0.48f * w, 0.08f * h)
        close()
    }
    val amber = Path().apply {
        moveTo(0.48f * w, 0.08f * h)
        lineTo(0.62f * w, 0.13f * h)
        lineTo(0.78f * w, 0.82f * h)
        lineTo(0.62f * w, 0.88f * h)
        close()
    }
    drawPath(blue, Color(0xFF00A5F6))
    drawPath(orange, Color(0xFFFF3E00))
    drawPath(amber, Color(0xFFFFB700))
}
