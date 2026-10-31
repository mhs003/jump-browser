package com.jump.browser.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jump.browser.data.AppSettings
import com.jump.browser.data.Bookmark
import com.jump.browser.data.DarkMode
import com.jump.browser.data.DownloadState
import com.jump.browser.data.DownloadTask
import com.jump.browser.data.HistoryEntry
import com.jump.browser.data.SearchEngine
import com.jump.browser.ui.theme.JumpAccent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ScreenScaffold(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChromeButton(JumpIcons.Back, "Back", onClick = onBack)
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                fontSize = 19.sp,
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
fun BookmarksScreen(
    bookmarks: List<Bookmark>,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onDelete: (Long) -> Unit,
    onEdit: (Bookmark) -> Unit,
    onAdd: (String, String) -> Unit,
) {
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    val filtered = remember(bookmarks, query) {
        if (query.isBlank()) bookmarks else bookmarks.filter {
            it.title.contains(query, true) || it.url.contains(query, true)
        }
    }
    ScreenScaffold(
        title = "Bookmarks",
        onBack = onBack,
        actions = {
            ChromeButton(JumpIcons.Search, "Search bookmarks") { searching = !searching }
            ChromeButton(JumpIcons.Plus, "Add bookmark") { showAdd = true }
        },
    ) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            if (searching) {
                SearchField(
                    query = query,
                    onQuery = { query = it },
                    placeholder = "Search bookmarks",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (filtered.isEmpty()) {
                EmptyState(
                    JumpIcons.Star,
                    if (bookmarks.isEmpty()) "No bookmarks yet" else "No matches",
                    "Bookmarks you save will show up here.",
                )
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(filtered, key = { it.id }) { bookmark ->
                        BookmarkRow(bookmark, onOpen, onDelete, onEdit)
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
    if (showAdd) {
        BookmarkEditDialog(
            initial = null,
            onDismiss = { showAdd = false },
            onSave = { title, url ->
                onAdd(title, url)
                showAdd = false
            },
        )
    }
}

@Composable
private fun BookmarkRow(
    bookmark: Bookmark,
    onOpen: (String) -> Unit,
    onDelete: (Long) -> Unit,
    onEdit: (Bookmark) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onOpen(bookmark.url) }
            .padding(start = 20.dp, end = 8.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = remember(bookmark.iconPath) {
            if (bookmark.iconPath.isBlank()) null
            else runCatching { BitmapFactory.decodeFile(bookmark.iconPath)?.asImageBitmap() }.getOrNull()
        }
        Box(
            Modifier
                .size(38.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(19.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = null,
                    modifier = Modifier.size(38.dp).clip(RoundedCornerShape(19.dp)),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    bookmark.title.take(1).uppercase().ifBlank { "•" },
                    color = JumpAccent,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(bookmark.title, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(
                bookmark.url,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            ChromeButton(JumpIcons.More, "Bookmark options") { menu = true }
            androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("Edit") },
                    onClick = { menu = false; onEdit(bookmark) },
                )
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = { menu = false; onDelete(bookmark.id) },
                )
            }
        }
    }
}

@Composable
fun BookmarkEditDialog(
    initial: Bookmark?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var url by remember { mutableStateOf(initial?.url ?: "") }
    JumpAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add bookmark" else "Edit bookmark") },
        text = {
            Column {
                TextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                )
                Spacer(Modifier.height(10.dp))
                TextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (url.isNotBlank()) onSave(title, url) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun HistoryScreen(
    entries: List<HistoryEntry>,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onDelete: (Long) -> Unit,
    onClear: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    val filtered = remember(entries, query) {
        if (query.isBlank()) entries else entries.filter {
            it.title.contains(query, true) || it.url.contains(query, true)
        }
    }
    val grouped = remember(filtered) {
        filtered.groupBy { Instant.ofEpochMilli(it.visitedAt).atZone(ZoneId.systemDefault()).toLocalDate() }
            .toSortedMap(compareByDescending { it })
    }
    ScreenScaffold(
        title = "History",
        onBack = onBack,
        actions = {
            ChromeButton(JumpIcons.Trash, "Clear history", enabled = entries.isNotEmpty()) {
                confirmClear = true
            }
        },
    ) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            SearchField(
                query = query,
                onQuery = { query = it },
                placeholder = "Search history",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (filtered.isEmpty()) {
                EmptyState(
                    JumpIcons.Clock,
                    if (entries.isEmpty()) "No history yet" else "No matches",
                    "Pages you visit will be listed here.",
                )
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    grouped.forEach { (day, dayEntries) ->
                        item(key = "h-$day") {
                            Text(
                                text = dayLabel(day),
                                modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 6.dp),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        items(dayEntries, key = { it.id }) { entry ->
                            HistoryRow(entry, onOpen, onDelete)
                        }
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
    if (confirmClear) {
        JumpAlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear history?") },
            text = { Text("All browsing history will be permanently deleted.") },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; onClear() }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry, onOpen: (String) -> Unit, onDelete: (Long) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onOpen(entry.url) }
            .padding(start = 20.dp, end = 4.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(entry.title, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(
                entry.url,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            timeLabel(entry.visitedAt),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Box(Modifier.size(40.dp).clickable { onDelete(entry.id) }, contentAlignment = Alignment.Center) {
            ChromeIcon(JumpIcons.Close, "Delete history entry", size = 17)
        }
    }
}

@Composable
fun DownloadsScreen(
    tasks: List<DownloadTask>,
    onBack: () -> Unit,
    onOpen: (DownloadTask) -> Unit,
    onDelete: (Long) -> Unit,
    onClearCompleted: () -> Unit,
) {
    ScreenScaffold(
        title = "Downloads",
        onBack = onBack,
        actions = {
            ChromeButton(JumpIcons.Trash, "Clear completed", enabled = tasks.isNotEmpty()) {
                onClearCompleted()
            }
        },
    ) {
        if (tasks.isEmpty()) {
            EmptyState(
                JumpIcons.Download,
                "No downloads",
                "Files you download in Jump will appear here.",
                Modifier.navigationBarsPadding(),
            )
        } else {
            LazyColumn(Modifier.fillMaxSize().navigationBarsPadding()) {
                items(tasks, key = { it.id }) { task ->
                    DownloadRow(task, onOpen, onDelete)
                    DividerLine(Modifier.padding(start = 68.dp))
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(task: DownloadTask, onOpen: (DownloadTask) -> Unit, onDelete: (Long) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = task.state == DownloadState.COMPLETE) { onOpen(task) }
            .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(JumpIcons.Download, contentDescription = null, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(task.title, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Text(
                when (task.state) {
                    DownloadState.COMPLETE -> "Completed · ${task.sizeText}"
                    DownloadState.FAILED -> "Failed"
                    DownloadState.PAUSED -> "Paused · ${task.sizeText}"
                    DownloadState.PENDING -> "Queued"
                    DownloadState.RUNNING -> task.sizeText.ifBlank { "Downloading…" }
                },
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (task.state == DownloadState.RUNNING || task.state == DownloadState.PENDING) {
                Spacer(Modifier.height(5.dp))
                LinearProgressIndicator(
                    progress = { task.fraction },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = JumpAccent,
                    trackColor = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
        Box(Modifier.size(40.dp).clickable { onDelete(task.id) }, contentAlignment = Alignment.Center) {
            ChromeIcon(JumpIcons.Close, "Remove download", size = 18)
        }
    }
}

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    onClearData: () -> Unit,
    onAbout: () -> Unit,
) {
    var dialog by remember { mutableStateOf<String?>(null) }
    var homepageDraft by remember { mutableStateOf(settings.homepageUrl) }
    ScreenScaffold(title = "Settings", onBack = onBack) {
        LazyColumn(Modifier.fillMaxSize().navigationBarsPadding()) {
            item { SectionHeader("General") }
            item {
                SettingsRow(
                    "Search engine",
                    settings.searchEngine.name,
                ) { dialog = "engine" }
            }
            item {
                SettingsRow(
                    "Homepage",
                    settings.homepageUrl.ifBlank { "Jump Homepage" },
                ) { homepageDraft = settings.homepageUrl; dialog = "homepage" }
            }
            item {
                SettingsRow(
                    "Appearance",
                    when (settings.darkMode) {
                        DarkMode.SYSTEM -> "Follow system"
                        DarkMode.LIGHT -> "Light"
                        DarkMode.DARK -> "Dark"
                    },
                ) { dialog = "theme" }
            }
            item { SectionHeader("Privacy") }
            item {
                SettingsSwitchRow("Ad blocking", "Blocks known ad and tracker hosts", settings.adBlock) {
                    onSettingsChange { s -> s.copy(adBlock = it) }
                }
            }
            item {
                SettingsSwitchRow("Block third-party cookies", "Improves privacy on some sites", settings.blockThirdPartyCookies) {
                    onSettingsChange { s -> s.copy(blockThirdPartyCookies = it) }
                }
            }
            item {
                SettingsSwitchRow("Do not track", "Ask sites not to track browsing", settings.doNotTrack) {
                    onSettingsChange { s -> s.copy(doNotTrack = it) }
                }
            }
            item {
                SettingsRow("Clear browsing data", "History, cookies and cache") {
                    dialog = "clear"
                }
            }
            item { SectionHeader("Content") }
            item {
                SettingsSwitchRow("JavaScript", "Allow sites to run scripts", settings.javaScript) {
                    onSettingsChange { s -> s.copy(javaScript = it) }
                }
            }
            item {
                SettingsSwitchRow("Load images", "Show images on web pages", settings.loadImages) {
                    onSettingsChange { s -> s.copy(loadImages = it) }
                }
            }
            item {
                SettingsSwitchRow("Search suggestions", "Fetch suggestions while typing", settings.searchSuggestions) {
                    onSettingsChange { s -> s.copy(searchSuggestions = it) }
                }
            }
            item {
                SettingsRow("Text scaling", "${settings.textZoom}%") { dialog = "zoom" }
            }
            item { SectionHeader("Browsing") }
            item {
                SettingsSwitchRow("Desktop site", "Request desktop versions by default", settings.desktopMode) {
                    onSettingsChange { s -> s.copy(desktopMode = it) }
                }
            }
            item {
                SettingsSwitchRow("Restore tabs", "Reopen tabs when Jump starts", settings.restoreTabs) {
                    onSettingsChange { s -> s.copy(restoreTabs = it) }
                }
            }
            item { SectionHeader("About") }
            item { SettingsRow("About Jump", "Version 1.0.0") { onAbout() } }
            item { Spacer(Modifier.height(28.dp)) }
        }
    }

    if (dialog == "engine") {
        ChoiceDialog(
            title = "Search engine",
            options = SearchEngine.all.map { it.name to it.id },
            selected = settings.searchEngineId,
            onDismiss = { dialog = null },
            onSelect = { id ->
                onSettingsChange { it.copy(searchEngineId = id) }
                dialog = null
            },
        )
    }
    if (dialog == "theme") {
        ChoiceDialog(
            title = "Appearance",
            options = listOf("Follow system" to "SYSTEM", "Light" to "LIGHT", "Dark" to "DARK"),
            selected = settings.darkMode.name,
            onDismiss = { dialog = null },
            onSelect = { value ->
                onSettingsChange { it.copy(darkMode = DarkMode.valueOf(value)) }
                dialog = null
            },
        )
    }
    if (dialog == "zoom") {
        ChoiceDialog(
            title = "Text scaling",
            options = listOf("80%" to "80", "90%" to "90", "100%" to "100", "110%" to "110", "120%" to "120"),
            selected = settings.textZoom.toString(),
            onDismiss = { dialog = null },
            onSelect = { value ->
                onSettingsChange { it.copy(textZoom = value.toInt()) }
                dialog = null
            },
        )
    }
    if (dialog == "homepage") {
        JumpAlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Homepage URL") },
            text = {
                Column {
                    Text("Leave blank to use the built-in Jump homepage.", fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    TextField(
                        value = homepageDraft,
                        onValueChange = { homepageDraft = it },
                        singleLine = true,
                        placeholder = { Text("https://example.com") },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onSettingsChange { it.copy(homepageUrl = homepageDraft.trim()) }
                    dialog = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
        )
    }
    if (dialog == "clear") {
        JumpAlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Clear browsing data?") },
            text = { Text("This clears history, cookies and the page cache. Bookmarks and downloads are kept.") },
            confirmButton = {
                TextButton(onClick = { dialog = null; onClearData() }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SettingsRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            value,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(6.dp))
        ChromeIcon(JumpIcons.ChevronRight, null, size = 16, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsSwitchRow(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChecked(!checked) }
            .padding(horizontal = 20.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
fun ChoiceDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    JumpAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (label, value) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(value) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        if (value == selected) {
                            ChromeIcon(JumpIcons.Check, null, size = 19, tint = JumpAccent)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun SearchField(
    query: String,
    onQuery: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = false,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(autoFocus) { if (autoFocus) runCatching { focus.requestFocus() } }
    Row(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChromeIcon(JumpIcons.Search, null, size = 18, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(placeholder, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                cursorBrush = SolidColor(JumpAccent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {}),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        if (query.isNotEmpty()) {
            Box(Modifier.size(30.dp).clickable { onQuery("") }, contentAlignment = Alignment.Center) {
                ChromeIcon(JumpIcons.Close, null, size = 15)
            }
        }
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
