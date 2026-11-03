package com.jump.browser.ui

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
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Surface(
        modifier = Modifier.fillMaxWidth().height(40.dp).blockFallThrough(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 0.dp,
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text("Find in page", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQuery,
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onNext() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            if (query.isNotEmpty()) {
                Text(
                    if (matches.second == 0) "0/0" else "${matches.first + 1}/${matches.second}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                ChromeButton(ReferenceIcons.ChevronUp, "Previous match", size = 22, onClick = onPrev)
                ChromeButton(ReferenceIcons.ChevronDown, "Next match", size = 22, onClick = onNext)
            }
            ChromeButton(ReferenceIcons.Close, "Close find", size = 22, onClick = onClose)
        }
    }
}

@Composable
fun ErrorPage(
    message: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .blockFallThrough()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            ReferenceIcons.Globe,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(54.dp),
        )
        Spacer(Modifier.height(18.dp))
        Text("Can't reach this page", fontSize = 20.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            message ?: "Check your connection and try again.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 18.sp,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRetry) { Text("Try again") }
    }
}

