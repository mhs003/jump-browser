package com.jump.browser.ui

import android.graphics.BitmapFactory
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
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
import android.widget.Toast
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
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
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
    var title by remember(initial?.id) { mutableStateOf(initial?.title ?: "") }
    var url by remember(initial?.id) { mutableStateOf(initial?.url ?: "") }
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
fun RecordsScreen(
    initialTab: Int,
    bookmarks: List<Bookmark>,
    history: List<HistoryEntry>,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onDeleteBookmark: (Long) -> Unit,
    onEditBookmark: (Bookmark) -> Unit,
    onAddBookmark: (String, String) -> Unit,
    onDeleteHistory: (Long) -> Unit,
    onClearHistory: () -> Unit,
) {
    val context = LocalContext.current
    var tab by remember(initialTab) { mutableIntStateOf(initialTab.coerceIn(0, 2)) }
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var showAdd by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val labels = listOf("Bookmarks", "History", "Saved pages")
    val filteredBookmarks = remember(bookmarks, query) {
        if (query.isBlank()) bookmarks else bookmarks.filter {
            it.title.contains(query, true) || it.url.contains(query, true)
        }
    }
    val filteredHistory = remember(history, query) {
        if (query.isBlank()) history else history.filter {
            it.title.contains(query, true) || it.url.contains(query, true)
        }
    }
    val groupedHistory = remember(filteredHistory) {
        filteredHistory.groupBy { Instant.ofEpochMilli(it.visitedAt).atZone(ZoneId.systemDefault()).toLocalDate() }
            .toSortedMap(compareByDescending { it })
    }
    val hasRows = when (tab) {
        0 -> filteredBookmarks.isNotEmpty()
        1 -> filteredHistory.isNotEmpty()
        else -> false
    }

    fun resetMode() {
        editing = false
        selected = emptySet()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            ChromeButton(JumpIcons.Back, "Back", onClick = onBack)
            Row(
                Modifier.weight(1f).padding(start = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(25.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                labels.forEachIndexed { index, label ->
                    Text(
                        label,
                        fontSize = 16.sp,
                        fontWeight = if (tab == index) FontWeight.Bold else FontWeight.Normal,
                        color = if (tab == index) MaterialTheme.colorScheme.onBackground
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.clickable {
                            tab = index
                            query = ""
                            resetMode()
                        }.padding(vertical = 4.dp),
                    )
                }
            }
        }
        DividerLine()
        LibrarySearchField(query, { query = it }, Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (!hasRows) {
                EmptyMeme()
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    when (tab) {
                        0 -> items(filteredBookmarks, key = { "b${it.id}" }) { bookmark ->
                            RecordRow(
                                title = bookmark.title.ifBlank { bookmark.url },
                                subtitle = bookmark.url,
                                icon = { BookmarkRecordIcon(bookmark) },
                                editing = editing,
                                selected = bookmark.id in selected,
                                onClick = {
                                    if (editing) selected = selected.toggle(bookmark.id)
                                    else onOpen(bookmark.url)
                                },
                                onLongClick = { onEditBookmark(bookmark) },
                            )
                        }
                        1 -> groupedHistory.forEach { (day, entries) ->
                            item(key = "day-$day") {
                                Text(
                                    text = day.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                                )
                            }
                            items(entries, key = { "h${it.id}" }) { entry ->
                                RecordRow(
                                    title = entry.title.ifBlank { entry.url },
                                    subtitle = entry.url,
                                    icon = { ChromeIcon(JumpIcons.Clock, null, size = 22) },
                                    editing = editing,
                                    selected = entry.id in selected,
                                    onClick = {
                                        if (editing) selected = selected.toggle(entry.id)
                                        else onOpen(entry.url)
                                    },
                                )
                            }
                        }
                    }
                    item { Spacer(Modifier.height(12.dp)) }
                }
            }
        }
        DividerLine()
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(48.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (editing) {
                Text(
                    "Select all",
                    fontSize = 16.sp,
                    modifier = Modifier.clickable {
                        selected = when (tab) {
                            0 -> filteredBookmarks.map { it.id }.toSet()
                            1 -> filteredHistory.map { it.id }.toSet()
                            else -> emptySet()
                        }
                    }.padding(vertical = 8.dp),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "Delete",
                    fontSize = 16.sp,
                    color = if (selected.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.clickable(enabled = selected.isNotEmpty()) {
                        if (tab == 0) selected.forEach(onDeleteBookmark) else selected.forEach(onDeleteHistory)
                        resetMode()
                    }.padding(vertical = 8.dp),
                )
                Spacer(Modifier.width(28.dp))
                Text(
                    "Done",
                    fontSize = 16.sp,
                    modifier = Modifier.clickable { resetMode() }.padding(vertical = 8.dp),
                )
            } else {
                Text(
                    if (tab == 1) "Tabs" else "More",
                    fontSize = 16.sp,
                    modifier = Modifier.clickable {
                        when {
                            tab == 1 -> Toast.makeText(context, "Use the toolbar to switch tabs", Toast.LENGTH_SHORT).show()
                            tab == 0 -> showAdd = true
                            else -> Toast.makeText(context, "No saved pages", Toast.LENGTH_SHORT).show()
                        }
                    }.padding(vertical = 8.dp),
                )
                Spacer(Modifier.weight(1f))
                if (tab == 1) {
                    Text(
                        "Delete all",
                        fontSize = 16.sp,
                        color = if (history.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.clickable(enabled = history.isNotEmpty()) { confirmClear = true }
                            .padding(vertical = 8.dp),
                    )
                    Spacer(Modifier.width(28.dp))
                }
                Text(
                    "Edit",
                    fontSize = 16.sp,
                    color = if (hasRows) MaterialTheme.colorScheme.onBackground
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable(enabled = hasRows) { editing = true; selected = emptySet() }
                        .padding(vertical = 8.dp),
                )
            }
        }
    }

    if (showAdd) {
        BookmarkEditDialog(
            initial = null,
            onDismiss = { showAdd = false },
            onSave = { title, url -> onAddBookmark(title, url); showAdd = false },
        )
    }
    if (confirmClear) {
        JumpAlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear history?") },
            text = { Text("All browsing history will be permanently deleted.") },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; onClearHistory() }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

private fun Set<Long>.toggle(id: Long): Set<Long> = if (id in this) this - id else this + id

@Composable
private fun LibrarySearchField(
    query: String,
    onQuery: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFFF2F2F2))
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (query.isEmpty()) {
            Text("Search", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        BasicTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun EmptyMeme() {
    Box(Modifier.fillMaxSize().padding(bottom = 20.dp), contentAlignment = Alignment.Center) {
        Text("¯\\_(ツ)_/¯", fontSize = 22.sp, color = Color(0xFF808080))
    }
}

@Composable
private fun BookmarkRecordIcon(bookmark: Bookmark) {
    val icon = remember(bookmark.iconPath) {
        if (bookmark.iconPath.isBlank()) null
        else runCatching { BitmapFactory.decodeFile(bookmark.iconPath)?.asImageBitmap() }.getOrNull()
    }
    Box(Modifier.size(30.dp).background(Color(0xFFE8EDFC), CircleShape), contentAlignment = Alignment.Center) {
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = null,
                modifier = Modifier.size(30.dp).clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                bookmark.title.trim().take(1).uppercase().ifBlank { "•" },
                color = JumpAccent,
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun RecordRow(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    editing: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { icon() }
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(title, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (editing) {
            Box(
                Modifier
                    .size(20.dp)
                    .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else Color(0xFFD0D0D0), CircleShape)
                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) ChromeIcon(JumpIcons.Check, null, size = 14, tint = Color.White)
            }
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
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    val context = LocalContext.current
    val filters = listOf("All", "Documents", "Archives", "APK", "Images", "Videos")
    val filtered = remember(tasks, query, filter) {
        tasks.filter { task ->
            val matchesText = query.isBlank() || task.title.contains(query, true) || task.url.contains(query, true)
            matchesText && when (filter) {
                1 -> task.isDocument()
                2 -> task.isArchive()
                3 -> task.isApk()
                4 -> task.isImage()
                5 -> task.isVideo()
                else -> true
            }
        }
    }
    val grouped = remember(filtered) {
        filtered.groupBy { Instant.ofEpochMilli(it.createdAt).atZone(ZoneId.systemDefault()).toLocalDate() }
            .toSortedMap(compareByDescending { it })
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            ChromeButton(JumpIcons.Back, "Back", onClick = onBack)
            Text("Downloads", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        }
        DividerLine()
        LibrarySearchField(query, { query = it }, Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            filters.forEachIndexed { index, label ->
                val active = filter == index
                Box(
                    Modifier
                        .height(24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, if (active) MaterialTheme.colorScheme.primary else Color(0xFFD3D3D3), RoundedCornerShape(12.dp))
                        .clickable { filter = index }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        fontSize = 14.sp,
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (grouped.isEmpty()) {
                EmptyMeme()
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    grouped.forEach { (day, dayTasks) ->
                        item(key = "d-$day") {
                            Text(
                                day.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                            )
                        }
                        items(dayTasks, key = { it.id }) { task ->
                            DownloadRecordRow(
                                task,
                                editing,
                                task.id in selected,
                                onClick = {
                                    if (editing) selected = selected.toggle(task.id)
                                    else if (task.state == DownloadState.COMPLETE) onOpen(task)
                                },
                            )
                        }
                    }
                    item { Spacer(Modifier.height(12.dp)) }
                }
            }
        }
        DividerLine()
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(48.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (editing) {
                Text("Select all", fontSize = 16.sp, modifier = Modifier.clickable {
                    selected = filtered.map { it.id }.toSet()
                }.padding(vertical = 8.dp))
                Spacer(Modifier.weight(1f))
                Text(
                    "Delete",
                    fontSize = 16.sp,
                    color = if (selected.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.clickable(enabled = selected.isNotEmpty()) {
                        selected.forEach(onDelete)
                        selected = emptySet()
                        editing = false
                    }.padding(vertical = 8.dp),
                )
                Spacer(Modifier.width(28.dp))
                Text("Done", fontSize = 16.sp, modifier = Modifier.clickable { editing = false; selected = emptySet() }.padding(vertical = 8.dp))
            } else {
                Text("New", fontSize = 16.sp, modifier = Modifier.clickable {
                    Toast.makeText(context, "Start a download from a web page", Toast.LENGTH_SHORT).show()
                }.padding(vertical = 8.dp))
                Spacer(Modifier.weight(1f))
                Text(
                    "Edit",
                    fontSize = 16.sp,
                    color = if (tasks.isNotEmpty()) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable(enabled = tasks.isNotEmpty()) { editing = true }.padding(vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun DownloadRecordRow(
    task: DownloadTask,
    editing: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
            Icon(JumpIcons.Download, contentDescription = null, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(task.title, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(
                when (task.state) {
                    DownloadState.COMPLETE -> task.sizeText
                    DownloadState.FAILED -> "Failed"
                    DownloadState.RUNNING -> task.sizeText.ifBlank { "Downloading…" }
                    DownloadState.PENDING -> "Queued"
                    DownloadState.PAUSED -> "Paused"
                },
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (task.state == DownloadState.RUNNING || task.state == DownloadState.PENDING) {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { task.fraction },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
        if (editing) {
            Box(
                Modifier
                    .size(20.dp)
                    .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else Color(0xFFD0D0D0), CircleShape)
                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) ChromeIcon(JumpIcons.Check, null, size = 14, tint = Color.White)
            }
        }
    }
}

private fun DownloadTask.isApk(): Boolean = title.endsWith(".apk", true) || mime.contains("android.package")
private fun DownloadTask.isImage(): Boolean = mime.startsWith("image/") || title.substringAfterLast('.', "").lowercase() in setOf("png", "jpg", "jpeg", "gif", "webp", "svg", "bmp")
private fun DownloadTask.isVideo(): Boolean = mime.startsWith("video/") || title.substringAfterLast('.', "").lowercase() in setOf("mp4", "mkv", "webm", "avi", "mov", "flv")
private fun DownloadTask.isArchive(): Boolean = title.substringAfterLast('.', "").lowercase() in setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz")
private fun DownloadTask.isDocument(): Boolean = mime.startsWith("text/") || mime.contains("pdf") || mime.contains("word") || mime.contains("document") || mime.contains("sheet") || mime.contains("presentation") || title.substringAfterLast('.', "").lowercase() in setOf("pdf", "doc", "docx", "txt", "xls", "xlsx", "ppt", "pptx", "csv")

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    onClearData: () -> Unit,
    onAbout: () -> Unit,
) {
    val context = LocalContext.current
    var category by remember { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = category != null) { category = null }
    var homepageDraft by remember { mutableStateOf(settings.homepageUrl) }
    var customSearchDraft by remember { mutableStateOf(settings.customSearchUrl) }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    ScreenScaffold(
        title = category ?: "Settings",
        onBack = { if (category != null) category = null else onBack() },
    ) {
        when (category) {
            null -> {
                LazyColumn(Modifier.fillMaxSize().navigationBarsPadding()) {
                    listOf("General", "Customization", "Privacy", "Advanced", "Scripts", "About").forEach { title ->
                        item { SettingsLabelRow(title) { category = title } }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
            "General" -> {
                LazyColumn(Modifier.fillMaxSize().navigationBarsPadding()) {
                    item { SettingsLabelRow("Sync") { toast("Sync is not enabled") } }
                    item { SettingsLabelRow("User-agent", "Default") { toast("Uses the mobile user agent") } }
                    item { SettingsLabelRow("Clear data") { dialog = "clear" } }
                    item { SettingsLabelRow("Ad blocking", if (settings.adBlock) null else "Off") {
                        onSettingsChange { it.copy(adBlock = !it.adBlock) }
                    } }
                    item { SettingsLabelRow("Site configuration") { toast("Configure sites from the page menu") } }
                    item { SettingsLabelRow("Password manager") { toast("No saved passwords") } }
                    item { SettingsLabelRow("Night mode", when (settings.darkMode) {
                        DarkMode.SYSTEM -> "Follow system"
                        DarkMode.LIGHT -> null
                        DarkMode.DARK -> "Dark"
                    }) { dialog = "theme" } }
                    item { SettingsLabelRow("Reader mode", if (settings.readerMode) "On" else null) {
                        onSettingsChange { it.copy(readerMode = !it.readerMode) }
                    } }
                    item { SettingsLabelRow("Toolbars settings") { toast("Address bar position: top") } }
                    item { SettingsLabelRow("Customize menu") { toast("Menu customization is not available") } }
                    item { SettingsLabelRow("Customize context menu") { toast("Context menu customization is not available") } }
                    item { SettingsLabelRow("Language", "System") { toast("Language follows Android") } }
                    item { SettingsLabelRow("Homepage", settings.homepageUrl.ifBlank { null }) {
                        homepageDraft = settings.homepageUrl; dialog = "homepage"
                    } }
                    item { SettingsLabelRow("Search settings", if (settings.searchEngineId == "google") null else settings.searchEngine.name) { dialog = "engine" } }
                    if (settings.searchEngineId == "custom") {
                        item { SettingsLabelRow("Custom search URL", settings.customSearchUrl.ifBlank { "Not set" }) {
                            customSearchDraft = settings.customSearchUrl; dialog = "customSearch"
                        } }
                    }
                    item { SettingsLabelRow("AI settings") { toast("AI is not enabled") } }
                    item { SettingsLabelRow("Orientation", "System") { toast("Orientation follows the system") } }
                    item { SettingsLabelRow("Download location") { toast("Public Downloads folder") } }
                    item { SettingsLabelRow("Download manager") { toast("Android DownloadManager") } }
                    item { SettingsLabelRow("External video player") { toast("Videos open in the system player") } }
                    item { SettingsLabelRow("Clear data on exit", "Off") { toast("Browsing data is kept on exit") } }
                    item { SettingsLabelRow("Font", if (settings.textZoom == 100) null else "${settings.textZoom}%") { dialog = "zoom" } }
                    item { SettingsLabelRow("Gestures") { toast("Gesture settings are not available") } }
                    item { SettingsLabelRow("Import/export bookmarks") { toast("Bookmark folders are not available") } }
                    item { SettingsLabelRow("Import data") { toast("Nothing to import") } }
                    item { SettingsLabelRow("Export data") { toast("Nothing to export") } }
                    item { SettingsLabelRow("Restore tabs on startup", if (settings.restoreTabs) null else "Off") {
                        onSettingsChange { it.copy(restoreTabs = !it.restoreTabs) }
                    } }
                    item { SettingsLabelRow("Set as default browser") { toast("Set Jump as the default browser in Android settings") } }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
            "Customization" -> {
                LazyColumn(Modifier.fillMaxSize().navigationBarsPadding()) {
                    item { SettingsLabelRow("Homepage", settings.homepageUrl.ifBlank { null }) {
                        homepageDraft = settings.homepageUrl; dialog = "homepage"
                    } }
                    item { SettingsLabelRow("Appearance", when (settings.darkMode) {
                        DarkMode.SYSTEM -> "Follow system"
                        DarkMode.LIGHT -> "Light"
                        DarkMode.DARK -> "Dark"
                    }) { dialog = "theme" } }
                    item { SettingsLabelRow("Font", if (settings.textZoom == 100) null else "${settings.textZoom}%") { dialog = "zoom" } }
                    item { SettingsLabelRow("Customize menu") { toast("Menu customization is not available") } }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
            "Privacy" -> {
                LazyColumn(Modifier.fillMaxSize().navigationBarsPadding()) {
                    item { SettingsLabelRow("Do not track", if (settings.doNotTrack) "On" else null) {
                        onSettingsChange { it.copy(doNotTrack = !it.doNotTrack) }
                    } }
                    item { SettingsLabelRow("Disable WebRTC") { toast("WebRTC uses Android defaults") } }
                    item { SettingsLabelRow("Do not sell or share data",
                        "Request the website not to sell or share my data to protect privacy",
                    ) { toast("Do not sell requests are sent with page loads") } }
                    item { SettingsLabelRow("Block third-party cookies", if (settings.blockThirdPartyCookies) "On" else null) {
                        onSettingsChange { it.copy(blockThirdPartyCookies = !it.blockThirdPartyCookies) }
                    } }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
            "Advanced" -> {
                LazyColumn(Modifier.fillMaxSize().navigationBarsPadding()) {
                    item { SettingsLabelRow("Save data", "Enable data-savings mode on compatible websites") {
                        toast("Data saver is controlled by the website")
                    } }
                    item { SettingsLabelRow("Show sniffer button automatically") { toast("Resource sniffer is not available") } }
                    item { SettingsLabelRow("Enable webpage debugging") { toast("Debugging is available in debug builds") } }
                    item { SettingsLabelRow("Disable predictive back gesture") { toast("Predictive back uses Android defaults") } }
                    item { SettingsLabelRow("Disable Custom Tabs") { toast("Custom Tabs are not supported") } }
                    item { SettingsLabelRow("Disable safe browsing", "Safe browsing protects against malware and phishing") {
                        toast("Safe browsing uses Android defaults")
                    } }
                    item { SettingsLabelRow("Ignore SSL certificate warnings", "Ignoring warnings may make connections insecure") {
                        toast("SSL warnings are always enforced")
                    } }
                    item { SettingsLabelRow("JavaScript", if (settings.javaScript) null else "Off") {
                        onSettingsChange { it.copy(javaScript = !it.javaScript) }
                    } }
                    item { SettingsLabelRow("Load images", if (settings.loadImages) null else "Off") {
                        onSettingsChange { it.copy(loadImages = !it.loadImages) }
                    } }
                    item { SettingsLabelRow("Search suggestions", if (settings.searchSuggestions) null else "Off") {
                        onSettingsChange { it.copy(searchSuggestions = !it.searchSuggestions) }
                    } }
                    item { SettingsLabelRow("Desktop site by default", if (settings.desktopMode) "On" else null) {
                        onSettingsChange { it.copy(desktopMode = !it.desktopMode) }
                    } }
                    item { SettingsLabelRow("Text scaling", "${settings.textZoom}%") { dialog = "zoom" } }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
            "Scripts" -> {
                LazyColumn(Modifier.fillMaxSize().navigationBarsPadding()) {
                    item { SettingsLabelRow("Userscripts", "No scripts installed") { toast("Script support is not available") } }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
            "About" -> {
                LazyColumn(Modifier.fillMaxSize().navigationBarsPadding()) {
                    item { SettingsLabelRow("Jump", "Version 1.0.0") { onAbout() } }
                    item { SettingsLabelRow("Open source licenses") { toast("MIT License") } }
                    item { SettingsLabelRow("Terms of use") { toast("Personal and educational use") } }
                    item { SettingsLabelRow("Privacy policy") { toast("Jump keeps browsing data on this device") } }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
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
                if (id == "custom") {
                    customSearchDraft = settings.customSearchUrl.ifBlank { "https://www.google.com/search?q=%s" }
                    dialog = "customSearch"
                } else dialog = null
            },
        )
    }
    if (dialog == "theme") {
        ChoiceDialog(
            title = "Appearance",
            options = listOf("Follow system" to "SYSTEM", "Light" to "LIGHT", "Dark" to "DARK"),
            selected = settings.darkMode.name,
            onDismiss = { dialog = null },
            onSelect = { id ->
                onSettingsChange { it.copy(darkMode = DarkMode.valueOf(id)) }
                dialog = null
            },
        )
    }
    if (dialog == "zoom") {
        ChoiceDialog(
            title = "Text scaling",
            options = listOf("80%" to "80", "90%" to "90", "100%" to "100", "115%" to "115", "130%" to "130", "150%" to "150"),
            selected = settings.textZoom.toString(),
            onDismiss = { dialog = null },
            onSelect = { id -> onSettingsChange { it.copy(textZoom = id.toInt()) }; dialog = null },
        )
    }
    if (dialog == "customSearch") {
        JumpAlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Custom search URL") },
            text = {
                Column {
                    Text("Use %s where the search query should go.", fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    TextField(
                        value = customSearchDraft,
                        onValueChange = { customSearchDraft = it },
                        singleLine = true,
                        placeholder = { Text("https://example.com/search?q=%s") },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    var template = customSearchDraft.trim()
                    if (template.isNotEmpty() && !template.contains("%s")) {
                        template += (if (template.contains("?")) "&q=%s" else "?q=%s")
                    }
                    onSettingsChange { it.copy(customSearchUrl = template, searchEngineId = "custom") }
                    dialog = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
        )
    }
    if (dialog == "homepage") {
        JumpAlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Homepage") },
            text = {
                TextField(
                    value = homepageDraft,
                    onValueChange = { homepageDraft = it },
                    singleLine = true,
                    placeholder = { Text("Leave empty for Jump Homepage") },
                )
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
private fun SettingsLabelRow(
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 22.5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
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
