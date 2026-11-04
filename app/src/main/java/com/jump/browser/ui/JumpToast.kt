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
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.TextStyle
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
import androidx.compose.ui.viewinterop.AndroidView
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
    // Platform-View pill (ChromeViews.kt) over the same toast bus.
    AndroidView(
        modifier = modifier,
        factory = { ctx -> ToastHostView(ctx) },
        update = { it.bind(request) },
    )
}
