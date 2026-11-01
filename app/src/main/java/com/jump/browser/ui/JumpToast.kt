package com.jump.browser.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jump.browser.ui.theme.JumpAccent
import kotlinx.coroutines.delay

data class JumpToastData(
    val id: Long,
    val text: String,
    val action: String?,
    val onAction: (() -> Unit)?,
    val duration: Long,
)

/**
 * The reference app routes short messages through its own toast host rather
 * than the platform toast. The host is attached to the browser window so the
 * pill, elevation, insets and duration stay under our control.
 */
object JumpToast {
    var current by mutableStateOf<JumpToastData?>(null)
        private set

    private var counter = 0L

    fun show(
        context: Context?,
        message: String,
        action: String? = null,
        long: Boolean = action != null,
        onAction: (() -> Unit)? = null,
    ) {
        if (message.isBlank()) return
        val data = JumpToastData(
            id = ++counter,
            text = message,
            action = action,
            onAction = onAction,
            duration = if (long) 3500L else 1600L,
        )
        current = data
    }

    fun dismiss() {
        current = null
    }

    fun dismissIf(id: Long) {
        if (current?.id == id) current = null
    }
}

@Composable
fun JumpToastHost(modifier: Modifier = Modifier) {
    val request = JumpToast.current

    // The host keeps rendering the outgoing message while the exit animation
    // runs; the visible state is intentionally not cleared with the request.
    var visibleData by remember { mutableStateOf<JumpToastData?>(null) }
    LaunchedEffect(request) {
        if (request != null) visibleData = request
    }
    LaunchedEffect(request?.id) {
        val id = request?.id ?: return@LaunchedEffect
        delay(request?.duration ?: 1600L)
        JumpToast.dismissIf(id)
    }

    val density = LocalDensity.current
    val lift = with(density) { 20.dp.roundToPx() }
    Box(
        modifier
            .fillMaxSize()
            .padding(bottom = 120.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = request != null,
            enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { lift },
            exit = fadeOut(tween(180)) + slideOutVertically(tween(180)) { lift },
        ) {
            val data = visibleData ?: return@AnimatedVisibility
            Row(
                Modifier
                    .widthIn(max = 360.dp)
                    .clip(RoundedCornerShape(30.dp))
                    .background(Color(0xCC1E1E1E))
                    .padding(start = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    data.text,
                    color = Color.White,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    textAlign = TextAlign.Start,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(vertical = 13.dp),
                )
                data.action?.let { label ->
                    Text(
                        label,
                        color = JumpAccent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .jumpClickable {
                                data.onAction?.invoke()
                                JumpToast.dismiss()
                            }
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                    )
                }
                if (data.action == null) {
                    Box(Modifier.padding(end = 16.dp))
                }
            }
        }
    }
}
