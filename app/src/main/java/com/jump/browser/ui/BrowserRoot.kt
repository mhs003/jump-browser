package com.jump.browser.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility as AnimVis
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.jump.browser.MainActivity
import com.jump.browser.browser.BrowserController
import com.jump.browser.browser.ReaderData
import com.jump.browser.browser.TabInfo
import com.jump.browser.browser.UrlUtils
import com.jump.browser.data.AppContainer
import com.jump.browser.data.AppSettings
import com.jump.browser.data.DarkMode
import com.jump.browser.data.DownloadTask
import com.jump.browser.ui.theme.JumpAccent

private enum class LibraryPage { NONE, BOOKMARKS, HISTORY, DOWNLOADS, SETTINGS }

@Composable
fun BrowserRoot(
    controller: BrowserController,
    container: AppContainer,
    dark: Boolean,
    onExit: () -> Unit,
) {
    val settings = container.settings.settings
    val bookmarks = container.bookmarks.bookmarks
    val history = container.history.entries
    val downloads = container.downloads.tasks
    val active = controller.activeTab
    val keyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val navInsetPx = WindowInsets.navigationBars.getBottom(density)
    val bottomInsetPx = navInsetPx + with(density) { 48.dp.roundToPx() }

    var editing by remember { mutableStateOf(false) }
    var address by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue("")) }
    var suggestions by remember { mutableStateOf(emptyList<SearchSuggestion>()) }
    var showMenu by remember { mutableStateOf(false) }
    var showTabs by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf(LibraryPage.NONE) }
    var reader by remember { mutableStateOf<ReaderData?>(null) }
    var source by remember { mutableStateOf<String?>(null) }
    var sourceTitle by remember { mutableStateOf("View source") }
    var findVisible by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var qrScreen by remember { mutableStateOf(false) }
    var aboutDialog by remember { mutableStateOf(false) }
    var helpDialog by remember { mutableStateOf(false) }
    var siteInfoDialog by remember { mutableStateOf(false) }
    var toolsDialog by remember { mutableStateOf(false) }
    var exitDialog by remember { mutableStateOf(false) }
    var editingBookmark by remember { mutableStateOf<com.jump.browser.data.Bookmark?>(null) }
    var lastBack by remember { mutableLongStateOf(0L) }

    LaunchedEffect(dark) {
        controller.isDarkTheme = dark
    }

    fun closeAllOverlays() {
        showMenu = false
        showTabs = false
        editing = false
        qrScreen = false
        toolsDialog = false
        keyboard?.hide()
    }

    fun openPage(target: LibraryPage) {
        closeAllOverlays()
        page = target
    }

    fun startAddressEditing() {
        val text = active?.url ?: ""
        address = androidx.compose.ui.text.input.TextFieldValue(
            text = text,
            selection = androidx.compose.ui.text.TextRange(0, text.length),
        )
        editing = true
        showMenu = false
        showTabs = false
    }

    fun closeEditing() {
        editing = false
        keyboard?.hide()
    }

    fun submitAddress() {
        val value = address.text.trim()
        if (value.isNotEmpty()) controller.navigate(value)
        closeEditing()
    }

    fun closeFind() {
        findVisible = false
        findQuery = ""
        controller.clearFind()
        keyboard?.hide()
    }

    fun exitApp() {
        if (settings.confirmExit) exitDialog = true else onExit()
    }

    BackHandler {
        when {
            exitDialog -> exitDialog = false
            qrScreen -> qrScreen = false
            aboutDialog -> aboutDialog = false
            helpDialog -> helpDialog = false
            siteInfoDialog -> siteInfoDialog = false
            toolsDialog -> toolsDialog = false
            editingBookmark != null -> editingBookmark = null
            showMenu -> showMenu = false
            showTabs -> showTabs = false
            page != LibraryPage.NONE -> page = LibraryPage.NONE
            reader != null -> reader = null
            source != null -> source = null
            findVisible -> closeFind()
            editing -> closeEditing()
            active?.canGoBack == true -> controller.goBack()
            else -> {
                val now = System.currentTimeMillis()
                if (now - lastBack < 2000) onExit()
                else {
                    lastBack = now
                    Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(editing, address.text, settings.searchSuggestions) {
        if (!editing) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        val query = address.text.trim()
        if (query.isEmpty() || !UrlUtils.isSearchQuery(query)) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(220)
        suggestions = SuggestionSource.load(
            settings.searchEngine,
            query,
            history,
            bookmarks,
            settings.searchSuggestions,
        )
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            TopChrome(
                editing = editing,
                tab = active,
                addressValue = address,
                onAddressValueChange = { address = it },
                onOpenAddress = { startAddressEditing() },
                onCloseAddress = { closeEditing() },
                onSubmitAddress = { submitAddress() },
                onExpandAddress = { keyboard?.hide() },
                onLeftClick = { siteInfoDialog = true },
                onRightClick = {
                    if (active?.isHome != false) qrScreen = true
                    else if (active?.loading == true) controller.stopLoading()
                    else controller.reload()
                },
                onTitleLongClick = { controller.copyUrl(active?.url) },
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    factory = { ctx ->
                        FrameLayout(ctx).also(controller::attachHost)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                if (active?.loading == true && !editing) {
                    LinearProgressIndicator(
                        progress = { (active!!.progress.coerceIn(0, 100)) / 100f },
                        modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().height(2.dp),
                        color = JumpAccent,
                        trackColor = Color.Transparent,
                    )
                }
                if (active == null || active.isHome) {
                    HomePage(
                        bookmarks = bookmarks,
                        onSearchClick = { startAddressEditing() },
                        onBookmarkClick = { bookmark -> controller.loadUrl(bookmark.url) },
                    )
                } else if (active.errorCode != null) {
                    ErrorPage(active.errorText, onRetry = { controller.reload() })
                }

                AddressSuggestionsOverlay(
                    visible = editing && active != null,
                    active = active,
                    suggestions = suggestions,
                    modifier = Modifier.align(Alignment.TopCenter),
                    onCurrent = { closeEditing() },
                    onPick = { suggestion ->
                        if (suggestion.kind == SuggestionKind.CURRENT) closeEditing()
                        else {
                            controller.navigate(suggestion.url ?: suggestion.text)
                            closeEditing()
                        }
                    },
                )

                if (findVisible) {
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().imePadding()) {
                        FindBar(
                            query = findQuery,
                            onQuery = {
                                findQuery = it
                                controller.findAll(it)
                            },
                            matches = controller.findState,
                            onNext = { controller.findNext(true) },
                            onPrev = { controller.findNext(false) },
                            onClose = { closeFind() },
                        )
                    }
                }
            }
            BottomBar(
                tab = active,
                tabCount = controller.tabs.size,
                onBack = { controller.goBack() },
                onForward = { controller.goForward() },
                onHome = { closeAllOverlays(); controller.goHome() },
                onTabs = {
                    if (showTabs) showTabs = false
                    else {
                        showMenu = false
                        closeEditing()
                        showTabs = true
                    }
                },
                onMenu = {
                    if (showMenu) showMenu = false
                    else {
                        showTabs = false
                        closeEditing()
                        showMenu = true
                    }
                },
            )
        }

        MenuOverlay(
            visible = showMenu,
            tab = active,
            bookmarked = active?.url?.let { container.bookmarks.isBookmarked(it) } == true,
            isDark = dark,
            onDismiss = { showMenu = false },
            onAction = { action ->
                showMenu = false
                when (action) {
                    MenuAction.NIGHT -> {
                        container.settings.update {
                            it.copy(darkMode = if (dark) DarkMode.LIGHT else DarkMode.DARK)
                        }
                    }
                    MenuAction.BOOKMARKS -> openPage(LibraryPage.BOOKMARKS)
                    MenuAction.HISTORY -> openPage(LibraryPage.HISTORY)
                    MenuAction.DOWNLOADS -> openPage(LibraryPage.DOWNLOADS)
                    MenuAction.SETTINGS -> openPage(LibraryPage.SETTINGS)
                    MenuAction.INCOGNITO -> controller.setIncognito(active?.incognito != true)
                    MenuAction.SHARE -> controller.shareUrl(active?.url, active?.title)
                    MenuAction.ADD_BOOKMARK -> {
                        val url = active?.url
                        if (url == null) Toast.makeText(context, "Open a page first", Toast.LENGTH_SHORT).show()
                        else {
                            val existing = bookmarks.firstOrNull { it.url == url }
                            if (existing != null) editingBookmark = existing else controller.toggleBookmark()
                        }
                    }
                    MenuAction.DESKTOP -> {
                        if (active?.url == null) Toast.makeText(context, "Open a page first", Toast.LENGTH_SHORT).show()
                        else controller.setDesktopMode(active.desktopMode.not())
                    }
                    MenuAction.TOOLS -> toolsDialog = true
                    MenuAction.NEW_TAB -> controller.newHomeTab()
                    MenuAction.NEW_INCOGNITO_TAB -> controller.newTab(null, incognito = true)
                    MenuAction.FIND -> {
                        findVisible = true
                        findQuery = ""
                    }
                    MenuAction.READER -> controller.extractReader { data ->
                        if (data == null) Toast.makeText(context, "Reader mode is not available for this page", Toast.LENGTH_SHORT).show()
                        else reader = data
                    }
                    MenuAction.SAVE_PAGE -> controller.savePage()
                    MenuAction.ADD_HOME -> addShortcut(context, active?.title ?: "Jump", active?.url)
                    MenuAction.TRANSLATE -> {
                        val url = active?.url
                        if (url == null) Toast.makeText(context, "Open a page first", Toast.LENGTH_SHORT).show()
                        else controller.loadUrl("https://translate.google.com/?sl=auto&tl=en&u=" + UrlUtils.encodeQuery(url))
                    }
                    MenuAction.VIEW_SOURCE -> controller.viewSource { html ->
                        if (html.isBlank()) Toast.makeText(context, "Source is not available", Toast.LENGTH_SHORT).show()
                        else {
                            sourceTitle = active?.title ?: "View source"
                            source = html
                        }
                    }
                    MenuAction.PRINT -> controller.printPage()
                    MenuAction.PAGE_INFO -> siteInfoDialog = true
                    MenuAction.COPY_URL -> controller.copyUrl(active?.url)
                    MenuAction.RELOAD -> controller.reload()
                    MenuAction.HOME -> controller.goHome()
                    MenuAction.HELP -> helpDialog = true
                    MenuAction.EXIT -> exitApp()
                }
            },
        )

        controller.contextMenu?.let { menu ->
            ContextMenuOverlay(
                menu = menu,
                onDismiss = { controller.dismissContextMenu() },
                onOpenNew = {
                    menu.linkUrl?.let { controller.openInNewTab(it) }
                    menu.imageUrl?.let { if (menu.linkUrl == null) controller.openInNewTab(it) }
                    controller.dismissContextMenu()
                },
                onOpenBackground = {
                    menu.linkUrl?.let { controller.openInNewTab(it, background = true) }
                    controller.dismissContextMenu()
                },
                onCopy = {
                    controller.copyUrl(menu.linkUrl ?: menu.imageUrl)
                    controller.dismissContextMenu()
                },
                onShare = {
                    controller.shareUrl(menu.linkUrl ?: menu.imageUrl, menu.title)
                    controller.dismissContextMenu()
                },
                onDownload = {
                    menu.imageUrl?.let { controller.downloadUrl(it, "image/*") }
                    controller.dismissContextMenu()
                },
            )
        }

        TabPopup(
            visible = showTabs,
            tabs = controller.tabs,
            activeId = controller.activeId,
            onSelect = { id -> controller.selectTab(id); showTabs = false },
            onClose = { id -> controller.closeTab(id) },
            onNewTab = { controller.newHomeTab(); showTabs = false },
            onDismiss = { showTabs = false },
            bottomInset = bottomInsetPx,
        )

        AnimVis(
            visible = qrScreen,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(140)),
        ) {
            Surface(Modifier.fillMaxSize(), color = Color.Black) {
                QrScannerScreen(
                    onBack = { qrScreen = false },
                    onResult = { value ->
                        qrScreen = false
                        controller.navigate(value)
                    },
                )
            }
        }

        AnimVis(
            visible = page != LibraryPage.NONE,
            enter = slideInHorizontally(tween(230)) { it / 4 } + fadeIn(tween(160)),
            exit = slideOutHorizontally(tween(190)) { it / 4 } + fadeOut(tween(120)),
        ) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                when (page) {
                    LibraryPage.BOOKMARKS -> BookmarksScreen(
                        bookmarks = bookmarks,
                        onBack = { page = LibraryPage.NONE },
                        onOpen = { controller.loadUrl(it); page = LibraryPage.NONE },
                        onDelete = { container.bookmarks.remove(it) },
                        onEdit = { editingBookmark = it },
                        onAdd = { title, url -> container.bookmarks.add(title, url) },
                    )
                    LibraryPage.HISTORY -> HistoryScreen(
                        entries = history,
                        onBack = { page = LibraryPage.NONE },
                        onOpen = { controller.loadUrl(it); page = LibraryPage.NONE },
                        onDelete = { container.history.delete(it) },
                        onClear = { container.history.clear() },
                    )
                    LibraryPage.DOWNLOADS -> DownloadsScreen(
                        tasks = downloads,
                        onBack = { page = LibraryPage.NONE },
                        onOpen = { openDownload(context, it) },
                        onDelete = { container.downloads.remove(it) },
                        onClearCompleted = { container.downloads.clearCompleted() },
                    )
                    LibraryPage.SETTINGS -> SettingsScreen(
                        settings = settings,
                        onBack = { page = LibraryPage.NONE },
                        onSettingsChange = { transform ->
                            container.settings.update(transform)
                            controller.applySettingsToAll()
                        },
                        onClearData = {
                            controller.clearBrowsingData(true, true, true)
                        },
                        onAbout = { aboutDialog = true },
                    )
                    LibraryPage.NONE -> Unit
                }
            }
        }

        AnimVis(
            visible = reader != null,
            enter = slideInHorizontally(tween(230)) { it / 4 } + fadeIn(tween(160)),
            exit = slideOutHorizontally(tween(190)) { it / 4 } + fadeOut(tween(120)),
        ) {
            reader?.let {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    ReaderScreen(it) { reader = null }
                }
            }
        }

        AnimVis(
            visible = source != null,
            enter = slideInHorizontally(tween(230)) { it / 4 } + fadeIn(tween(160)),
            exit = slideOutHorizontally(tween(190)) { it / 4 } + fadeOut(tween(120)),
        ) {
            source?.let {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SourceScreen(it, sourceTitle) { source = null }
                }
            }
        }
    }

    if (editingBookmark != null) {
        BookmarkEditDialog(
            initial = editingBookmark,
            onDismiss = { editingBookmark = null },
            onSave = { title, url ->
                editingBookmark?.let { old ->
                    container.bookmarks.update(old.id, title, url, old.folder)
                }
                editingBookmark = null
            },
        )
    }

    if (toolsDialog) {
        ToolsDialog(
            onDismiss = { toolsDialog = false },
            onReader = {
                toolsDialog = false
                controller.extractReader { data ->
                    if (data != null) reader = data else Toast.makeText(context, "Reader mode is not available", Toast.LENGTH_SHORT).show()
                }
            },
            onSource = {
                toolsDialog = false
                controller.viewSource { html -> sourceTitle = active?.title ?: "View source"; source = html }
            },
            onPrint = { toolsDialog = false; controller.printPage() },
            onTranslate = {
                toolsDialog = false
                active?.url?.let {
                    controller.loadUrl("https://translate.google.com/?sl=auto&tl=en&u=" + UrlUtils.encodeQuery(it))
                }
            },
            onPageInfo = { toolsDialog = false; siteInfoDialog = true },
            onFind = { toolsDialog = false; findVisible = true; findQuery = "" },
        )
    }

    if (siteInfoDialog) {
        val tab = active
        JumpAlertDialog(
            onDismissRequest = { siteInfoDialog = false },
            title = { Text(if (tab?.isHome == true) "Homepage" else UrlUtils.displayHost(tab?.url)) },
            text = {
                Column {
                    Text(
                        if (tab?.secure == true) "Connection is secure" else "Connection is not secure",
                        color = if (tab?.secure == true) JumpAccent else MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(tab?.url ?: "jump://homepage", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    Text("Cookies: ${if (settings.blockThirdPartyCookies) "First-party only" else "Allowed"}", fontSize = 12.sp)
                    Text("JavaScript: ${if (settings.javaScript) "Allowed" else "Blocked"}", fontSize = 12.sp)
                    Text("Ad and tracker requests blocked: ${controller.adBlockedCount}", fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { controller.copyUrl(tab?.url); siteInfoDialog = false }) { Text("Copy URL") }
            },
            dismissButton = {
                TextButton(onClick = { controller.shareUrl(tab?.url, tab?.title); siteInfoDialog = false }) { Text("Share") }
            },
        )
    }

    if (aboutDialog) {
        JumpAlertDialog(
            onDismissRequest = { aboutDialog = false },
            title = { Text("Jump") },
            text = {
                Column {
                    Text("A fast, lightweight browser.", fontSize = 14.sp)
                    Spacer(Modifier.height(10.dp))
                    Text("Version 1.0.0", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text("Kotlin · Jetpack Compose · Android WebView", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = { aboutDialog = false }) { Text("Close") } },
        )
    }

    if (helpDialog) {
        JumpAlertDialog(
            onDismissRequest = { helpDialog = false },
            title = { Text("Help") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("• Tap the address bar to search or enter a URL.", fontSize = 13.sp)
                    Text("• Tap the bottom tab count to switch tabs.", fontSize = 13.sp)
                    Text("• Long-press a link to open it in a new tab.", fontSize = 13.sp)
                    Text("• Use the menu for reader mode, find in page and more.", fontSize = 13.sp)
                }
            },
            confirmButton = { TextButton(onClick = { helpDialog = false }) { Text("Got it") } },
        )
    }

    if (exitDialog) {
        var dontAsk by remember { mutableStateOf(false) }
        JumpAlertDialog(
            onDismissRequest = { exitDialog = false },
            title = { Text("Exit") },
            text = {
                Column {
                    Text("All open tabs will be deleted and cannot be restored, are you sure to you want to exit app?")
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(
                            checked = dontAsk,
                            onCheckedChange = { dontAsk = it },
                        )
                        Text("Don't remind me again", fontSize = 14.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (dontAsk) container.settings.update { it.copy(confirmExit = false) }
                    exitDialog = false
                    onExit()
                }) { Text("Exit") }
            },
            dismissButton = { TextButton(onClick = { exitDialog = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun AddressSuggestionsOverlay(
    visible: Boolean,
    active: TabInfo?,
    suggestions: List<SearchSuggestion>,
    modifier: Modifier = Modifier,
    onCurrent: () -> Unit,
    onPick: (SearchSuggestion) -> Unit,
) {
    AnimVis(
        visible = visible,
        enter = fadeIn(tween(120)) + slideInVertically(tween(180)) { -it / 5 },
        exit = fadeOut(tween(90)) + slideOutVertically(tween(120)) { -it / 8 },
        modifier = modifier,
    ) {
        SuggestionDropdown(
            current = active,
            suggestions = suggestions,
            onCurrent = onCurrent,
            onPick = onPick,
        )
    }
}

@Composable
private fun SuggestionDropdown(
    current: TabInfo?,
    suggestions: List<SearchSuggestion>,
    onCurrent: () -> Unit,
    onPick: (SearchSuggestion) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
        shadowElevation = 5.dp,
    ) {
        Column {
            if (current != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .clickable { onCurrent() }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Favicon(current, 24)
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            current.title.ifBlank { "Homepage" },
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!current.url.isNullOrBlank()) {
                            Text(
                                current.url,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    ChromeIcon(JumpIcons.External, null, size = 18, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (suggestions.isNotEmpty()) DividerLine()
            }
            LazyColumn(Modifier.heightIn(max = 300.dp)) {
                items(suggestions) { suggestion ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .clickable { onPick(suggestion) }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ChromeIcon(
                            when (suggestion.kind) {
                                SuggestionKind.BOOKMARK -> JumpIcons.Star
                                SuggestionKind.HISTORY -> JumpIcons.Clock
                                else -> JumpIcons.Search
                            },
                            null,
                            size = 18,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.size(14.dp))
                        Text(
                            suggestion.text,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContextMenuOverlay(
    menu: com.jump.browser.browser.WebContextMenu,
    onDismiss: () -> Unit,
    onOpenNew: () -> Unit,
    onOpenBackground: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onDownload: () -> Unit,
) {
    AnimVis(visible = true, enter = fadeIn(tween(100)), exit = fadeOut(tween(100))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.28f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .clickable { },
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            ) {
                Column {
                    Text(
                        menu.linkUrl?.let { UrlUtils.displayHost(it) }
                            ?: menu.imageUrl?.let { UrlUtils.displayHost(it) }
                            ?: "Link",
                        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp, end = 20.dp),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (menu.linkUrl != null) {
                        ContextRow(JumpIcons.Plus, "Open in new tab", onOpenNew)
                        ContextRow(JumpIcons.Tabs, "Open in background", onOpenBackground)
                        ContextRow(JumpIcons.Copy, "Copy link", onCopy)
                        ContextRow(JumpIcons.Share, "Share link", onShare)
                    }
                    if (menu.imageUrl != null) {
                        ContextRow(JumpIcons.Image, "Open image", onOpenNew)
                        ContextRow(JumpIcons.Download, "Download image", onDownload)
                        ContextRow(JumpIcons.Copy, "Copy image address", onCopy)
                        ContextRow(JumpIcons.Share, "Share image", onShare)
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun ContextRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChromeIcon(icon, null, size = 21)
        Spacer(Modifier.size(18.dp))
        Text(label, fontSize = 15.sp)
    }
}

@Composable
private fun ToolsDialog(
    onDismiss: () -> Unit,
    onReader: () -> Unit,
    onSource: () -> Unit,
    onPrint: () -> Unit,
    onTranslate: () -> Unit,
    onPageInfo: () -> Unit,
    onFind: () -> Unit,
) {
    JumpAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tools") },
        text = {
            Column {
                ToolRow(JumpIcons.Reader, "Reader mode", onReader)
                ToolRow(JumpIcons.Find, "Find in page", onFind)
                ToolRow(JumpIcons.Copy, "View source", onSource)
                ToolRow(JumpIcons.Translate, "Translate", onTranslate)
                ToolRow(JumpIcons.Printer, "Print", onPrint)
                ToolRow(JumpIcons.Info, "Page info", onPageInfo)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun ToolRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChromeIcon(icon, null, size = 21)
        Spacer(Modifier.size(16.dp))
        Text(title, fontSize = 15.sp)
    }
}

private fun openDownload(context: Context, task: DownloadTask) {
    val uri = task.localUri ?: return
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(Uri.parse(uri), task.mime.ifBlank { "*/*" })
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }.onFailure {
        Toast.makeText(context, "No app can open this file", Toast.LENGTH_SHORT).show()
    }
}

private fun addShortcut(context: Context, title: String, url: String?) {
    if (url == null) {
        Toast.makeText(context, "Open a page first", Toast.LENGTH_SHORT).show()
        return
    }
    runCatching {
        val manager = context.getSystemService(Context.SHORTCUT_SERVICE) as ShortcutManager
        val shortcut = ShortcutInfo.Builder(context, "jump-" + url.hashCode())
            .setShortLabel(title.take(12).ifBlank { "Jump" })
            .setLongLabel(title.take(30).ifBlank { "Open in Jump" })
            .setIcon(Icon.createWithResource(context, android.R.drawable.ic_menu_search))
            .setIntent(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .setClassName(context.packageName, MainActivity::class.java.name)
            )
            .build()
        manager.requestPinShortcut(shortcut, null)
        Toast.makeText(context, "Shortcut requested", Toast.LENGTH_SHORT).show()
    }.onFailure {
        Toast.makeText(context, "Could not create shortcut", Toast.LENGTH_SHORT).show()
    }
}
