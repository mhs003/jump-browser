package com.jump.browser.ui

import android.view.ViewGroup
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.viewinterop.AndroidView

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import com.jump.browser.ui.theme.Icon
import com.jump.browser.ui.theme.MaterialTheme
import com.jump.browser.ui.theme.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jump.browser.browser.ReaderData
import com.jump.browser.ui.theme.JumpAccent
import com.jump.browser.ui.theme.JumpMuted
import com.jump.browser.ui.theme.Surface
import com.jump.browser.ui.theme.Button

@Composable
fun ReaderContent(data: ReaderData, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 28.dp, bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            data.title,
            modifier = Modifier.fillMaxWidth(),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 30.sp,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            com.jump.browser.browser.UrlUtils.displayHost(data.url),
            modifier = Modifier.fillMaxWidth(),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(28.dp))
        Text(
            data.text,
            fontSize = 18.sp,
            lineHeight = 31.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
fun SourceScreen(source: String, title: String, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    ScreenScaffold(
        title = title,
        onBack = onBack,
        actions = {
            ChromeButton(JumpIcons.Copy, "Copy source") {
                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Source", source))
            }
        },
    ) {
        SelectionContainer {
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Text(
                        text = source,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun FindBar(
    query: String,
    onQuery: (String) -> Unit,
    matches: Pair<Int, Int>,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onClose: () -> Unit,
) {
    // Platform-View pill (ChromeViews.kt); focus raised on first appearance.
    var focusedOnce by remember { mutableStateOf(false) }
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    AndroidView(
        factory = { ctx ->
            FindBarView(
                ctx,
                ViewPalette.forChrome(dark),
                onQuery = onQuery,
                onNext = onNext,
                onPrev = onPrev,
                onClose = onClose,
            )
        },
        update = {
            it.bind(query, matches, focusedOnce)
            focusedOnce = true
        },
    )
}@Composable
fun ErrorPage(
    message: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Platform-View page (ChromeViews.kt).
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            ErrorPageView(ctx, ViewPalette.forChrome(dark), onRetry).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
        },
        update = { it.bind(message) },
    )
}