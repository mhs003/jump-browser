package com.jump.browser.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility as AnimVis
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import com.jump.browser.ui.theme.MaterialTheme
import com.jump.browser.ui.theme.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.jump.browser.ui.JumpToast
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
import com.jump.browser.ui.theme.JumpScrim
import com.jump.browser.ui.theme.Surface
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

private enum class LibraryPage { NONE, RECORDS, DOWNLOADS, SETTINGS, SNIFFER, NETWORK, SAVED }

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
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val navInsetPx = WindowInsets.navigationBars.getBottom(density)
    val bottomInsetPx = navInsetPx + with(density) { 48.dp.roundToPx() }

    var editing by remember { mutableStateOf(false) }
    var titleMenu by remember { mutableStateOf(false) }
    var searchEngineDialog by remember { mutableStateOf(false) }
    var fullScreenAddress by remember { mutableStateOf(false) }
    var address by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue("")) }
    var suggestions by remember { mutableStateOf(emptyList<SearchSuggestion>()) }
    var showMenu by remember { mutableStateOf(false) }
    var showTabs by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf(LibraryPage.NONE) }
    var shownPage by remember { mutableStateOf(LibraryPage.NONE) }
    var savedPages by remember { mutableStateOf(emptyList<java.io.File>()) }
    var tts by remember { mutableStateOf<android.speech.tts.TextToSpeech?>(null) }
    var recordsTab by remember { mutableIntStateOf(0) }
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
    var homeDip by remember { mutableStateOf(false) }
    var clearDataDialog by remember { mutableStateOf(false) }
    var immersive by remember { mutableStateOf(false) }
    var lastBack by remember { mutableLongStateOf(0L) }
    val rootView = LocalView.current

    // Web pages are darkened by "Night mode", independently of the chrome
    // theme. When the chrome follows the system, the page follows suit.
    val pageDark = settings.nightMode || (settings.darkMode == DarkMode.SYSTEM && dark)
    LaunchedEffect(pageDark) {
        controller.isDarkTheme = pageDark
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            tts?.stop()
            tts?.shutdown()
            tts = null
        }
    }

    LaunchedEffect(homeDip) {
        if (homeDip) {
            kotlinx.coroutines.delay(160)
            homeDip = false
        }
    }

    LaunchedEffect(settings.fullscreen) {
        if (settings.fullscreen) immersive = true
    }

    LaunchedEffect(immersive) {
        val window = (context as? Activity)?.window ?: return@LaunchedEffect
        WindowCompat.getInsetsController(window, rootView).apply {
            if (immersive) hide(WindowInsetsCompat.Type.systemBars())
            else show(WindowInsetsCompat.Type.systemBars())
        }
    }

    fun closeAllOverlays() {
        controller.stopResourceSniffing()
        titleMenu = false
        showMenu = false
        showTabs = false
        editing = false
        fullScreenAddress = false
        qrScreen = false
        toolsDialog = false
        keyboard?.hide()
    }

    fun openPage(target: LibraryPage) {
        closeAllOverlays()
        page = target
        if (target != LibraryPage.NONE) shownPage = target
    }

    fun startAddressEditing(initialText: String? = null) {
        val text = initialText ?: active?.url ?: ""
        address = androidx.compose.ui.text.input.TextFieldValue(
            text = text,
            selection = androidx.compose.ui.text.TextRange(0, text.length),
        )
        editing = true
        fullScreenAddress = false
        titleMenu = false
        showMenu = false
        showTabs = false
    }

    fun clipboardText(): String {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
            as? android.content.ClipboardManager ?: return ""
        return clipboard.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
    }

    fun pasteIntoAddress() {
        titleMenu = false
        startAddressEditing(clipboardText())
    }

    fun pasteAndGo() {
        titleMenu = false
        val text = clipboardText().trim()
        if (text.isNotEmpty()) controller.navigate(text)
    }

    fun closeEditing() {
        editing = false
        fullScreenAddress = false
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

    fun closeWindow() {
        closeAllOverlays()
        onExit()
    }

    BackHandler {
        when {
            titleMenu -> titleMenu = false
            searchEngineDialog -> searchEngineDialog = false
            clearDataDialog -> clearDataDialog = false
            immersive -> immersive = false
            controller.hasFullscreen() -> controller.exitFullscreen()
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
            fullScreenAddress -> fullScreenAddress = false
            editing -> closeEditing()
            active?.canGoBack == true -> controller.goBack()
            else -> {
                val now = System.currentTimeMillis()
                if (now - lastBack < 2000) onExit()
                else {
                    lastBack = now
                    JumpToast.show(context, "Press back again to exit")
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

    val pageProgress by animateFloatAsState(
        targetValue = if (page != LibraryPage.NONE) 1f else 0f,
        animationSpec = tween(
            220,
            easing = androidx.compose.animation.core.Easing { t -> t * t * (3f - 2f * t) },
        ),
        label = "library-page",
    )
    val screenWidthPx = with(density) {
        LocalConfiguration.current.screenWidthDp.dp.toPx()
    }
    LaunchedEffect(page) {
        if (page != LibraryPage.NONE) shownPage = page
    }
    LaunchedEffect(pageProgress) {
        if (page == LibraryPage.NONE && pageProgress == 0f) shownPage = LibraryPage.NONE
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = -screenWidthPx * 0.30f * pageProgress },
        ) {
            val bottomToolbar = settings.toolbarPosition != "TOP"
            if (bottomToolbar) {
                Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                TopChrome(
                    editing = editing,
                    tab = active,
                    addressValue = address,
                    onAddressValueChange = { address = it },
                    onOpenAddress = { startAddressEditing() },
                    onCloseAddress = { closeEditing() },
                    onSubmitAddress = { submitAddress() },
                    onExpandAddress = { fullScreenAddress = true },
                    onLeftClick = { if (active?.isHome != false) searchEngineDialog = true else siteInfoDialog = true },
                    onRightClick = {
                        if (active?.isHome != false) qrScreen = true
                        else if (active?.loading == true) controller.stopLoading()
                        else controller.reload()
                    },
                    onTitleLongClick = { titleMenu = true },
                    onSecureClick = { if (active?.isHome != false) searchEngineDialog = true else siteInfoDialog = true },
                )
            }
            if (!bottomToolbar) {
                Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                TopChrome(
                    editing = editing,
                    tab = active,
                    addressValue = address,
                    onAddressValueChange = { address = it },
                    onOpenAddress = { startAddressEditing() },
                    onCloseAddress = { closeEditing() },
                    onSubmitAddress = { submitAddress() },
                    onExpandAddress = { fullScreenAddress = true },
                    onLeftClick = { if (active?.isHome != false) searchEngineDialog = true else siteInfoDialog = true },
                    onRightClick = {
                        if (active?.isHome != false) qrScreen = true
                        else if (active?.loading == true) controller.stopLoading()
                        else controller.reload()
                    },
                    onTitleLongClick = { titleMenu = true },
                    onSecureClick = { if (active?.isHome != false) searchEngineDialog = true else siteInfoDialog = true },
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    factory = { ctx ->
                        FrameLayout(ctx).also(controller::attachHost)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                if (active?.loading == true && !editing) {
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(Color.Transparent),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth((active.progress.coerceIn(0, 100)) / 100f)
                                .fillMaxSize()
                                .background(
                                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                                        listOf(Color(0xFF59C2E5), Color(0xFFA687E0)),
                                    )
                                ),
                        )
                    }
                }
                if (active == null || active.isHome) {
                    val homeOffset by animateFloatAsState(
                        targetValue = if (homeDip) 1f else 0f,
                        animationSpec = tween(120),
                        label = "home-dip",
                    )
                    HomePage(
                        bookmarks = bookmarks,
                        onSearchClick = { startAddressEditing() },
                        onBookmarkClick = { bookmark -> controller.loadUrl(bookmark.url) },
                        onLogoClick = { recordsTab = 0; openPage(LibraryPage.RECORDS) },
                        logoPath = settings.homepageLogoPath,
                        backgroundPath = settings.homepageBackgroundPath,
                        backgroundOpacity = settings.homepageBackgroundOpacity,
                        searchBarEnabled = settings.homepageSearchBar,
                        favoriteColorEnabled = settings.homepageFavoriteColor,
                        modifier = Modifier.graphicsLayer {
                            translationY = 72.dp.toPx() * homeOffset
                            alpha = 1f - 0.9f * homeOffset
                        },
                    )
                } else if (active.errorCode != null) {
                    ErrorPage(active.errorText, onRetry = { controller.reload() })
                }

                AddressScrim(visible = editing && !fullScreenAddress, onClick = { closeEditing() })

                AddressSuggestionsOverlay(
                    visible = editing && !fullScreenAddress && active != null,
                    active = active,
                    suggestions = suggestions,
                    showCurrent = address.text.isBlank() && active?.isHome != true,
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
            if (!bottomToolbar) {
                BottomBar(
                    tab = active,
                    tabs = controller.tabs,
                    onBack = { controller.goBack() },
                    onForward = { controller.goForward() },
                    onHome = { closeAllOverlays(); controller.goHome() },
                    onNewTab = { controller.newHomeTab() },
                    onMenuLong = { openPage(LibraryPage.SETTINGS) },
                    onBackLong = { controller.scrollToTop() },
                    onForwardLong = { controller.scrollToBottom() },
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
            } else {
                Column(Modifier.fillMaxWidth()) {
                    BottomBar(
                        tab = active,
                        tabs = controller.tabs,
                        onBack = { controller.goBack() },
                        onForward = { controller.goForward() },
                        onHome = { closeAllOverlays(); controller.goHome() },
                        onNewTab = { controller.newHomeTab() },
                        onMenuLong = { openPage(LibraryPage.SETTINGS) },
                        onBackLong = { controller.scrollToTop() },
                        onForwardLong = { controller.scrollToBottom() },
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
            }
        }

        MenuOverlay(
            visible = showMenu,
            bottomInset = bottomInsetPx,
            customOrder = settings.customizeMenu,
            tab = active,
            bookmarked = active?.url?.let { container.bookmarks.isBookmarked(it) } == true,
            isDark = dark,
            imagesEnabled = settings.loadImages,
            adBlocking = settings.adBlock,
            textZoom = settings.textZoom,
            onDismiss = { showMenu = false },
            onAction = { action ->
                showMenu = false
                when (action) {
                    MenuAction.NIGHT -> {
                        // The sheet's Night button flips the page darkening. If
                        // the chrome theme is pinned (not following the system),
                        // mirror the choice into it so the whole app switches
                        // together instead of the page and chrome disagreeing.
                        val next = !pageDark
                        container.settings.update {
                            it.copy(
                                nightMode = next,
                                darkMode = if (it.darkMode == DarkMode.SYSTEM) {
                                    DarkMode.SYSTEM
                                } else if (next) {
                                    DarkMode.DARK
                                } else {
                                    DarkMode.LIGHT
                                },
                            )
                        }
                        controller.applyDarkModeToAll()
                    }
                    MenuAction.BOOKMARKS -> { recordsTab = 0; openPage(LibraryPage.RECORDS) }
                    MenuAction.HISTORY -> { recordsTab = 1; openPage(LibraryPage.RECORDS) }
                    MenuAction.DOWNLOADS -> openPage(LibraryPage.DOWNLOADS)
                    MenuAction.SETTINGS -> openPage(LibraryPage.SETTINGS)
                    MenuAction.INCOGNITO -> controller.setIncognito(active?.incognito != true)
                    MenuAction.SHARE -> controller.shareUrl(active?.url, active?.title)
                    MenuAction.ADD_BOOKMARK -> {
                        val url = active?.url
                        if (url == null) JumpToast.show(context, "Open a page first")
                        else {
                            val existing = bookmarks.firstOrNull { it.url == url }
                            if (existing != null) editingBookmark = existing else controller.toggleBookmark()
                        }
                    }
                    MenuAction.DESKTOP -> {
                        if (active?.url == null) JumpToast.show(context, "Open a page first")
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
                        if (data == null) JumpToast.show(context, "Reader mode is not available for this page")
                        else reader = data
                    }
                    MenuAction.SAVE_PAGE -> controller.savePage()
                    MenuAction.ADD_HOME -> addShortcut(context, active?.title ?: "Jump", active?.url)
                    MenuAction.TRANSLATE -> {
                        val url = active?.url
                        if (url == null) JumpToast.show(context, "Open a page first")
                        else controller.loadUrl("https://translate.google.com/?sl=auto&tl=en&u=" + UrlUtils.encodeQuery(url))
                    }
                    MenuAction.VIEW_SOURCE -> controller.viewSource { html ->
                        if (html.isBlank()) JumpToast.show(context, "Source is not available")
                        else {
                            sourceTitle = active?.title ?: "View source"
                            source = html
                        }
                    }
                    MenuAction.SAVED_PAGES -> {
                        savedPages = loadSavedPages(context)
                        openPage(LibraryPage.SAVED)
                    }
                    MenuAction.FULLSCREEN -> immersive = !immersive
                    MenuAction.SHOW_IMAGES -> {
                        container.settings.update { it.copy(loadImages = !it.loadImages) }
                        controller.applySettingsToAll()
                        controller.reload()
                    }
                    MenuAction.RESOURCE_SNIFFER -> {
                        controller.startResourceSniffing()
                        openPage(LibraryPage.SNIFFER)
                    }
                    MenuAction.USER_AGENT -> {
                        if (active?.url == null) JumpToast.show(context, "Open a page first")
                        else controller.setDesktopMode(active.desktopMode.not())
                    }
                    MenuAction.NETWORK_LOG -> {
                        controller.startResourceSniffing()
                        openPage(LibraryPage.NETWORK)
                    }
                    MenuAction.SCAN_QR -> qrScreen = true
                    MenuAction.READ_ALOUD -> {
                        if (tts != null) {
                            tts?.stop()
                            tts?.shutdown()
                            tts = null
                            JumpToast.show(context, "Stopped reading aloud")
                        } else {
                            controller.readPageAloud { data ->
                                if (data == null || data.text.isBlank()) {
                                    JumpToast.show(context, "Nothing to read on this page")
                                } else {
                                    tts = startReadingAloud(context, data.text)
                                }
                            }
                        }
                    }
                    MenuAction.AI -> JumpToast.show(context, "AI is not enabled")
                    MenuAction.ORIENTATION -> JumpToast.show(context, "Orientation follows the system")
                    MenuAction.AD_BLOCK -> {
                        container.settings.update { it.copy(adBlock = !it.adBlock) }
                        controller.applySettingsToAll()
                        controller.reload()
                    }
                    MenuAction.MARK_AD -> controller.markSiteAsAdFree()
                    MenuAction.TEXT_SIZE -> openPage(LibraryPage.SETTINGS)
                    MenuAction.CLEAR_DATA -> clearDataDialog = true
                    MenuAction.CUSTOMIZE_MENU -> JumpToast.show(context, "Menu customization is not available")
                    MenuAction.PRINT -> controller.printPage()
                    MenuAction.PAGE_INFO -> siteInfoDialog = true
                    MenuAction.COPY_URL -> controller.copyUrl(active?.url)
                    MenuAction.RELOAD -> controller.reload()
                    MenuAction.HOME -> controller.goHome()
                    MenuAction.HELP -> helpDialog = true
                    MenuAction.ABOUT -> aboutDialog = true
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

        if (shownPage != LibraryPage.NONE) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = screenWidthPx * (1f - pageProgress) },
                color = MaterialTheme.colorScheme.background,
            ) {
                when (shownPage) {
                    LibraryPage.RECORDS -> RecordsScreen(
                        initialTab = recordsTab,
                        bookmarks = bookmarks,
                        history = history,
                        onBack = { page = LibraryPage.NONE },
                        onOpen = { controller.loadUrl(it); page = LibraryPage.NONE },
                        onDeleteBookmark = { container.bookmarks.remove(it) },
                        onEditBookmark = { editingBookmark = it },
                        onAddBookmark = { title, url -> container.bookmarks.add(title, url) },
                        onDeleteHistory = { container.history.delete(it) },
                        onClearHistory = { container.history.clear() },
                    )
                    LibraryPage.DOWNLOADS -> DownloadsScreen(
                        tasks = downloads,
                        onBack = { page = LibraryPage.NONE },
                        onOpen = { openDownload(context, it) },
                        onDelete = { container.downloads.remove(it) },
                        onClearCompleted = { container.downloads.clearCompleted() },
                    )
                    LibraryPage.SNIFFER -> ResourceSnifferScreen(
                        resources = controller.sniffedResources,
                        onBack = { page = LibraryPage.NONE },
                        onOpen = { controller.navigate(it); page = LibraryPage.NONE },
                        onDownload = { controller.downloadUrl(it) },
                        onCopy = { controller.copyUrl(it) },
                    )
                    LibraryPage.NETWORK -> NetworkLogScreen(
                        entries = controller.networkEntries,
                        onBack = { page = LibraryPage.NONE },
                        onClear = { controller.clearNetworkLog() },
                        onOpen = { controller.copyUrl(it) },
                    )
                    LibraryPage.SAVED -> SavedPagesScreen(
                        pages = savedPages,
                        onBack = { page = LibraryPage.NONE },
                        onOpen = { file ->
                            page = LibraryPage.NONE
                            controller.loadUrl("file://${file.absolutePath}")
                        },
                        onDelete = {
                            it.delete()
                            savedPages = loadSavedPages(context)
                        },
                    )
                    LibraryPage.SETTINGS -> SettingsScreen(
                        settings = settings,
                        onBack = { page = LibraryPage.NONE },
                        onSettingsChange = { transform ->
                            container.settings.update(transform)
                            controller.applySettingsToAll()
                        },
                        onClearData = {
                            val wasIncognito = active?.incognito == true
                            controller.closeAllTabs()
                            controller.clearBrowsingData(true, true, true)
                            controller.newHomeTab()
                            if (wasIncognito) controller.newTab(null, incognito = true)
                            page = LibraryPage.NONE
                        },
                        onAbout = { aboutDialog = true },
                    )
                    LibraryPage.NONE -> Unit
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

        if (editing && fullScreenAddress) {
            FullScreenAddressBar(
                value = address,
                onValueChange = { address = it },
                onSubmit = { submitAddress() },
                onCollapse = { fullScreenAddress = false },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (titleMenu) {
            LaunchedEffect(Unit) { showMenu = false; showTabs = false }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                    ) { titleMenu = false },
            )
        }
        if (searchEngineDialog) {
            SearchEngineDialog(
                selected = settings.searchEngineId,
                onDismiss = { searchEngineDialog = false },
                onSelect = { id ->
                    container.settings.update { it.copy(searchEngineId = id) }
                    controller.applySettingsToAll()
                    searchEngineDialog = false
                },
            )
        }

        if (titleMenu) {
            TopAnchoredPopup(onDismiss = { titleMenu = false }) {
                Box(Modifier.padding(start = 6.dp)) {
                TitleActionPopup(
                    showCopy = active?.isHome == false && active?.url?.isNotBlank() == true,
                    onCopy = {
                        titleMenu = false
                        controller.copyUrl(active?.url)
                    },
                    onPaste = { pasteIntoAddress() },
                    onPasteAndGo = { pasteAndGo() },
                )
                }
            }
        }

        JumpToastHost()
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
                    if (data != null) reader = data else JumpToast.show(context, "Reader mode is not available")
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
        // A real window with the popup animation, anchored under the title bar.
        TopAnchoredPopup(
            onDismiss = { siteInfoDialog = false },
            contentAlignment = Alignment.TopCenter,
        ) {
            Surface(
                Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp),
                shadowElevation = 0.dp,
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            BasicText(
                                text = if (tab?.isHome == true) "Homepage" else (tab?.title ?: "Site info"),
                                style = TextStyle(
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onBackground,
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (tab?.isHome != true) {
                                Spacer(Modifier.height(4.dp))
                                BasicText(
                                    text = tab?.url ?: "",
                                    style = TextStyle(
                                        fontSize = 14.sp,
                                        color = Color(0xFF008000),
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (tab?.isHome != true) {
                            ChromeButton(
                                icon = ReferenceIcons.Clock,
                                contentDescription = "History for this site",
                                size = 22,
                                onClick = {
                                    siteInfoDialog = false
                                    recordsTab = 1
                                    openPage(LibraryPage.RECORDS)
                                },
                            )
                            ChromeButton(
                                icon = ReferenceIcons.CustomizeMenu,
                                contentDescription = "Site configuration",
                                size = 22,
                                onClick = {
                                    JumpToast.show(context, "Site configuration is not available")
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    if (tab?.isHome != true) {
                        SiteInfoAction("View certificate") {
                            JumpToast.show(context, if (tab?.secure == true) "Connection is secure" else "Connection is not secure")
                            siteInfoDialog = false
                        }
                        SiteInfoAction("View Cookies") {
                            JumpToast.show(context, "No site cookies to display")
                            siteInfoDialog = false
                        }
                    }
                    SiteInfoAction("Site configuration") {
                        JumpToast.show(context, "Site configuration is not available")
                    }
                    if (tab?.isHome != true) {
                        SiteInfoAction("Show reader") {
                            siteInfoDialog = false
                            controller.extractReader { data ->
                                if (data == null) JumpToast.show(context, "Reader mode is not available for this page")
                                else reader = data
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
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
            confirmButton = { JumpDialogButton(onClick = { aboutDialog = false }) { Text("Close") } },
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
            confirmButton = { JumpDialogButton(onClick = { helpDialog = false }) { Text("Got it") } },
        )
    }

    if (clearDataDialog) {
        JumpAlertDialog(
            onDismissRequest = { clearDataDialog = false },
            title = { Text("Clear data") },
            text = { Text("Clear history, cookies and cached files?") },
            confirmButton = {
                JumpDialogButton(onClick = {
                    controller.clearBrowsingData(true, true, true)
                    clearDataDialog = false
                    JumpToast.show(context, "Browsing data cleared")
                }) { Text("Clear") }
            },
            dismissButton = { JumpDialogButton(onClick = { clearDataDialog = false }) { Text("Cancel") } },
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
                    Spacer(Modifier.height(12.dp))
                    JumpCheckbox(
                        checked = dontAsk,
                        onCheckedChange = { dontAsk = it },
                        label = "Don't remind me again",
                    )
                }
            },
            confirmButton = {
                JumpDialogButton(onClick = {
                    if (dontAsk) container.settings.update { it.copy(confirmExit = false) }
                    exitDialog = false
                    onExit()
                }) { Text("Exit") }
            },
            dismissButton = { JumpDialogButton(onClick = { exitDialog = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun AddressScrim(visible: Boolean, onClick: () -> Unit) {
    AnimVis(
        visible = visible,
        enter = fadeIn(tween(240)),
        exit = fadeOut(tween(240)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(JumpScrim)
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                ) { onClick() },
        )
    }
}

@Composable
private fun SearchEngineDialog(
    selected: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val options = com.jump.browser.data.SearchEngine.reference
    JumpAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Search engine") },
        text = {
            Column {
                options.forEach { engine ->
                    JumpOptionRow(
                        label = engine.name,
                        selected = engine.id == selected,
                        onClick = { onSelect(engine.id) },
                    )
                }
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun AddressSuggestionsOverlay(
    visible: Boolean,
    active: TabInfo?,
    suggestions: List<SearchSuggestion>,
    showCurrent: Boolean,
    modifier: Modifier = Modifier,
    onCurrent: () -> Unit,
    onPick: (SearchSuggestion) -> Unit,
) {
    AnimVis(
        visible = visible,
        enter = fadeIn(tween(240)),
        exit = fadeOut(tween(240)),
        modifier = modifier,
    ) {
        SuggestionDropdown(
            current = active,
            showCurrent = showCurrent,
            suggestions = suggestions,
            onCurrent = onCurrent,
            onPick = onPick,
        )
    }
}

@Composable
private fun SuggestionDropdown(
    current: TabInfo?,
    showCurrent: Boolean,
    suggestions: List<SearchSuggestion>,
    onCurrent: () -> Unit,
    onPick: (SearchSuggestion) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
        shadowElevation = 0.dp,
    ) {
        Column {
            if (current != null && showCurrent) {
                SuggestionRow(
                    icon = null,
                    tab = current,
                    title = current.title.ifBlank { "Page" },
                    subtitle = current.url,
                    onClick = onCurrent,
                )
                if (suggestions.isNotEmpty()) DividerLine()
            }
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(suggestions) { suggestion ->
                    SuggestionRow(
                        icon = when (suggestion.kind) {
                            SuggestionKind.BOOKMARK -> ReferenceIcons.BookmarkSuggestion
                            SuggestionKind.HISTORY -> ReferenceIcons.HistorySuggestion
                            else -> ReferenceIcons.Search
                        },
                        tab = null,
                        title = suggestion.text,
                        subtitle = suggestion.subtitle,
                        onClick = { onPick(suggestion) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    tab: TabInfo?,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .pressable { onClick() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            if (tab != null) {
                Favicon(tab, 24)
            } else if (icon != null) {
                ChromeIcon(icon, null, size = 22)
            }
        }
        Column(Modifier.weight(1f).padding(start = 2.dp)) {
            Text(
                title,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            ChromeIcon(ReferenceIcons.OpenArrow, null, size = 22)
        }
    }
}

@Composable
private fun SiteInfoAction(label: String, onClick: () -> Unit) {
    BasicText(
        text = label,
        style = TextStyle(
            fontSize = 16.sp,
            color = if (MaterialTheme.colorScheme.onBackground.luminance() > 0.5f) JumpAccent
            else Color(0xFF7F9EE8),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .pressable { onClick() }
            .padding(horizontal = 16.dp, vertical = 13.dp),
    )
}

private fun loadSavedPages(context: Context): List<java.io.File> {
    val dir = java.io.File(context.filesDir, "saved_pages")
    return dir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() }.orEmpty()
}

private fun startReadingAloud(
    context: Context,
    text: String,
): android.speech.tts.TextToSpeech? {
    var engine: android.speech.tts.TextToSpeech? = null
    engine = android.speech.tts.TextToSpeech(context) { status ->
        if (status == android.speech.tts.TextToSpeech.SUCCESS) {
            val result = engine?.setLanguage(java.util.Locale.getDefault())
            if (result == android.speech.tts.TextToSpeech.LANG_MISSING_DATA ||
                result == android.speech.tts.TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                engine?.setLanguage(java.util.Locale.US)
            }
            engine?.setSpeechRate(1.0f)
            val chunks = text.chunked(3500)
            chunks.forEachIndexed { index, chunk ->
                val mode = if (index == 0) android.speech.tts.TextToSpeech.QUEUE_FLUSH
                else android.speech.tts.TextToSpeech.QUEUE_ADD
                engine?.speak(chunk, mode, null, "jump-$index")
            }
            JumpToast.show(context, "Reading aloud")
        } else {
            JumpToast.show(context, "Text-to-speech is not available")
        }
    }
    return engine
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
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                ) { onDismiss() },
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                    ) { },
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
                        ContextRow(ReferenceIcons.Plus, "Open in new tab", onOpenNew)
                        ContextRow(ReferenceIcons.Tabs, "Open in background", onOpenBackground)
                        ContextRow(JumpIcons.Copy, "Copy link", onCopy)
                        ContextRow(ReferenceIcons.Share, "Share link", onShare)
                    }
                    if (menu.imageUrl != null) {
                        ContextRow(ReferenceIcons.ShowImages, "Open image", onOpenNew)
                        ContextRow(ReferenceIcons.Download, "Download image", onDownload)
                        ContextRow(JumpIcons.Copy, "Copy image address", onCopy)
                        ContextRow(ReferenceIcons.Share, "Share image", onShare)
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
            .pressable { onClick() }
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
                ToolRow(ReferenceIcons.Find, "Find in page", onFind)
                ToolRow(JumpIcons.Copy, "View source", onSource)
                ToolRow(ReferenceIcons.Translate, "Translate", onTranslate)
                ToolRow(JumpIcons.Printer, "Print", onPrint)
                ToolRow(JumpIcons.Info, "Page info", onPageInfo)
            }
        },
        confirmButton = { JumpDialogButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun ToolRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .pressable { onClick() }
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
        JumpToast.show(context, "No app can open this file")
    }
}

private fun addShortcut(context: Context, title: String, url: String?) {
    if (url == null) {
        JumpToast.show(context, "Open a page first")
        return
    }
    runCatching {
        val manager = context.getSystemService(Context.SHORTCUT_SERVICE) as ShortcutManager
        val shortcut = ShortcutInfo.Builder(context, "jump-" + url.hashCode())
            .setShortLabel(title.take(12).ifBlank { "Jump" })
            .setLongLabel(title.take(30).ifBlank { "Open in Jump" })
            .setIcon(Icon.createWithResource(context, com.jump.browser.R.drawable.ic_jump))
            .setIntent(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .setClassName(context.packageName, MainActivity::class.java.name)
            )
            .build()
        manager.requestPinShortcut(shortcut, null)
        JumpToast.show(context, "Shortcut requested")
    }.onFailure {
        JumpToast.show(context, "Could not create shortcut")
    }
}
