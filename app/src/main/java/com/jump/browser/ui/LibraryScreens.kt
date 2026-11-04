package com.jump.browser.ui

import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.viewinterop.AndroidView
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.content.pm.PackageManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import androidx.compose.foundation.Image
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.jump.browser.ui.theme.Icon
import com.jump.browser.ui.theme.MaterialTheme
import com.jump.browser.ui.theme.Surface
import com.jump.browser.ui.theme.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jump.browser.ui.JumpToast
import com.jump.browser.data.AppSettings
import com.jump.browser.data.Bookmark
import com.jump.browser.data.DarkMode
import com.jump.browser.data.DownloadState
import com.jump.browser.data.DownloadTask
import com.jump.browser.data.HistoryEntry
import com.jump.browser.data.SearchEngine
import com.jump.browser.browser.UserAgents
import com.jump.browser.ui.theme.JumpAccent
import com.jump.browser.ui.theme.LinearProgressIndicator
import com.jump.browser.ui.theme.Button
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun ScreenScaffold(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    headerHeight: androidx.compose.ui.unit.Dp = 48.dp,
    titleSize: androidx.compose.ui.unit.TextUnit = 16.sp,
    titleStartPadding: androidx.compose.ui.unit.Dp = 0.dp,
    titleBold: Boolean = true,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().height(headerHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChromeButton(ReferenceIcons.Back, "Back", onClick = onBack)
            Spacer(Modifier.width(titleStartPadding))
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                fontSize = titleSize,
                fontWeight = if (titleBold) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onBackground,
            )
            actions()
        }
        DividerLine()
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
    }
}

@Composable
fun BookmarkEditDialog(
    initial: Bookmark?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    // Platform-View card (ChromeViews.kt) inside the dialog window.
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
        ),
    ) {
        AndroidView(
            modifier = Modifier
                .padding(horizontal = 36.dp)
                .fillMaxWidth(),
            factory = { ctx ->
                BookmarkEditView(
                    ctx,
                    ViewPalette.forChrome(dark),
                    initial?.title.orEmpty(),
                    initial?.url.orEmpty(),
                    onSaved = { t, u ->
                        onSave(t, u)
                        onDismiss()
                    },
                    onDismissDialog = onDismiss,
                )
            },
            update = { },
        )
    }
}
private fun Set<Long>.toggle(id: Long): Set<Long> = if (id in this) this - id else this + id

@Composable
fun JumpOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(43.dp)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = com.jump.browser.ui.theme.JumpPressIndication,
                onClick = onClick,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(if (selected) JumpAccent else Color.Transparent)
                .border(
                    width = 1.5.dp,
                    color = if (selected) JumpAccent else MaterialTheme.colorScheme.outline,
                    shape = CircleShape,
                ),
        )
        Spacer(Modifier.width(20.dp))
        BasicText(
            text = label,
            style = TextStyle(
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground,
            ),
        )
    }
}

private fun dayLabel(day: LocalDate): String {
    val today = LocalDate.now()
    return when (day) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> day.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.getDefault()))
    }
}

private fun timeLabel(epoch: Long): String =
    Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))

internal fun pickHomepageImage(context: android.content.Context, onPicked: (String) -> Unit) {
    val activity = context as? com.jump.browser.MainActivity
    if (activity == null) {
        JumpToast.show(context, "Image selection is not available")
        return
    }
    activity.pickHomepageImage { path ->
        if (path == null) JumpToast.show(context, "No image selected")
        else onPicked(path)
    }
}


/** Via's group caption inside a settings list. */
internal fun openDefaultBrowserSettings(context: android.content.Context) {
    val intent = android.content.Intent(
        android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS,
    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }.onFailure {
        JumpToast.show(context, "Set Jump as the default browser in Android settings")
    }
}
