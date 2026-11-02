package com.jump.browser.browser

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Message
import android.print.PrintAttributes
import android.print.PrintManager
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.jump.browser.data.LoginAutofill
import com.jump.browser.ui.JumpToast
import com.jump.browser.data.AppContainer
import com.jump.browser.data.TabSnapshot
import com.jump.browser.data.TabStore
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicLong
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TabInfo(
    val id: Long,
    val url: String? = null,
    val title: String = "Homepage",
    val progress: Int = 0,
    val loading: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val secure: Boolean = false,
    val incognito: Boolean = false,
    val desktopMode: Boolean = false,
    val favicon: android.graphics.Bitmap? = null,
    val errorCode: Int? = null,
    val errorText: String? = null,
) {
    val isHome: Boolean get() = url == null
}

data class ReaderData(
    val title: String,
    val byline: String,
    val text: String,
    val url: String,
)

data class NetworkEntry(
    val method: String,
    val url: String,
    val mime: String,
    val status: Int,
    val startedAt: Long,
    val finishedAt: Long,
    val blocked: Boolean = false,
) {
    val durationMs: Long get() = (finishedAt - startedAt).coerceAtLeast(0)
    val host: String get() = runCatching { android.net.Uri.parse(url).host ?: url }.getOrDefault(url)
    val time: String
        get() = if (startedAt <= 0) "" else SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(startedAt))
}

data class SniffedResource(
    val url: String,
    val mime: String,
    val size: Long,
)

data class WebContextMenu(
    val linkUrl: String?,
    val imageUrl: String?,
    val title: String,
)

class BrowserController(
    private val activity: Activity,
    private val app: AppContainer,
    private val launchFileChooser: (Intent) -> Unit,
) {
    private class Session(
        var info: TabInfo,
        var webView: WebView? = null,
        var loadRequested: Boolean = false,
        var homeInHistory: Boolean = false,
    )

    private val sessions = LinkedHashMap<Long, Session>()
    private val ids = AtomicLong(1)
    private var host: FrameLayout? = null
    private var attached: WebView? = null
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var networkLogging = false

    /**
     * In-flight request start times, keyed by URL.
     *
     * Bounded and self-pruning: a cancelled or redirected request never
     * produces a response, so without eviction this grew for the whole session
     * and made every page load slower than the last.
     */
    private val networkStart = object : LinkedHashMap<String, Long>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>): Boolean =
            size > 200
    }

    // The debug panels read these; they are plain values published through a
    // bumped revision instead of Compose state, because assigning a new list on
    // every network response invalidated every composable that observes this
    // controller — even while the panels were closed.
    private var networkSnapshot: List<NetworkEntry> = emptyList()
    private var sniffedSnapshot: List<SniffedResource> = emptyList()
    var panelsRevision by mutableStateOf(0)
        private set

    val networkEntries: List<NetworkEntry> get() = networkSnapshot
    val sniffedResources: List<SniffedResource> get() = sniffedSnapshot

    var tabs by mutableStateOf<List<TabInfo>>(emptyList())
        private set
    var activeId by mutableStateOf<Long?>(null)
        private set
    var adBlockedCount by mutableStateOf(0)
        private set
    var contextMenu by mutableStateOf<WebContextMenu?>(null)
        private set
    var findState by mutableStateOf<Pair<Int, Int>>(0 to 0)
        private set

    val activeTab: TabInfo?
        get() = tabs.firstOrNull { it.id == activeId }

    val canGoBack: Boolean get() = activeTab?.canGoBack == true
    val canGoForward: Boolean get() = activeTab?.canGoForward == true

    private val desktopUa =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    init {
        CookieManager.getInstance().setAcceptCookie(true)
        restoreTabs()
    }

    // ---------------------------------------------------------------- tabs

    private fun publish() {
        tabs = sessions.values.map { it.info }
    }

    /**
     * Republishes a single tab.
     *
     * `update` used to call the full `publish()` for every change, and
     * `onProgressChanged` fires many times per page load, so each tick rebuilt
     * the whole list and invalidated every observer of [tabs]. Replacing just
     * the changed entry keeps the same state for Compose while touching one
     * element when the rest is already up to date.
     */
    private fun publish(id: Long) {
        val info = sessions[id]?.info ?: return
        val current = tabs
        val index = current.indexOfFirst { it.id == id }
        tabs = when {
            index < 0 -> current + info
            current[index] === info -> current
            else -> current.toMutableList().also { it[index] = info }
        }
    }

    private fun session(id: Long): Session? = sessions[id]

    private fun update(id: Long, transform: (TabInfo) -> TabInfo) {
        val s = sessions[id] ?: return
        s.info = transform(s.info)
        publish(id)
    }

    private fun homeUrl(): String? {
        val configured = app.settings.settings.homepageUrl.trim()
        if (configured.isEmpty()) return null
        return UrlUtils.toUrlOrSearch(configured, app.settings.settings.searchEngine)
    }

    fun newTab(
        url: String? = null,
        incognito: Boolean = app.settings.settings.incognitoDefault,
        select: Boolean = true,
        desktopMode: Boolean = app.settings.settings.desktopMode,
        title: String = "",
    ): Long {
        val id = ids.incrementAndGet()
        val target = url?.let { UrlUtils.toUrlOrSearch(it, app.settings.settings.searchEngine) }
        val info = TabInfo(
            id = id,
            url = target,
            title = when {
                !title.isNullOrBlank() -> title
                target == null -> "Homepage"
                else -> UrlUtils.titleFor(target)
            },
            incognito = incognito,
            desktopMode = desktopMode,
        )
        sessions[id] = Session(info)
        if (select) activeId = id
        publish()
        if (select) {
            if (target != null) loadInSession(id, target) else syncHost()
            saveState()
        }
        return id
    }

    fun newHomeTab(): Long {
        val configured = homeUrl()
        return newTab(configured)
    }

    fun selectTab(id: Long) {
        if (!sessions.containsKey(id)) return
        activeId = id
        syncHost()
        saveState()
    }

    fun closeTab(id: Long) {
        val s = sessions[id] ?: return
        val wasActive = activeId == id
        val order = sessions.keys.toList()
        val index = order.indexOf(id)
        destroyWebView(s)
        sessions.remove(id)
        if (sessions.isEmpty()) {
            activeId = null
            publish()
            newTab(null, incognito = false)
            return
        }
        if (wasActive) {
            val next = order.getOrNull(index - 1) ?: order.getOrNull(index + 1)
                ?: sessions.keys.last()
            activeId = next
        }
        publish()
        syncHost()
        saveState()
    }

    fun closeOtherTabs(keepId: Long) {
        sessions.keys.filter { it != keepId }.forEach { id ->
            sessions[id]?.let { destroyWebView(it) }
            sessions.remove(id)
        }
        activeId = keepId
        publish()
        syncHost()
        saveState()
    }

    fun closeAllTabs() {
        sessions.values.forEach { destroyWebView(it) }
        sessions.clear()
        activeId = null
        publish()
        newTab(null, incognito = false)
    }

    fun duplicateTab(id: Long) {
        val s = sessions[id] ?: return
        val url = s.info.url ?: return
        newTab(url, incognito = s.info.incognito, desktopMode = s.info.desktopMode)
    }

    fun moveTab(id: Long, delta: Int) {
        val keys = sessions.keys.toMutableList()
        val from = keys.indexOf(id)
        if (from < 0) return
        val to = (from + delta).coerceIn(0, keys.size - 1)
        if (from == to) return
        val entries = sessions.entries.toMutableList()
        val item = entries.removeAt(from)
        entries.add(to, item)
        sessions.clear()
        entries.forEach { (k, v) -> sessions[k] = v }
        publish()
    }

    private fun syncHost() {
        val frame = host ?: return
        val active = activeId?.let { sessions[it] }
        val desired = if (active?.info?.url != null) active.webView ?: createWebView(active.info.id) else null
        if (attached === desired) return
        while (frame.childCount > 0) frame.removeViewAt(0)
        attached = desired
        if (desired != null) {
            (desired.parent as? ViewGroup)?.removeView(desired)
            if (active != null && !active.loadRequested) {
                active.info.url?.let {
                    active.loadRequested = true
                    desired.loadUrl(it)
                }
            }
            frame.addView(
                desired,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
        }
    }

    fun attachHost(frame: FrameLayout) {
        host = frame
        syncHost()
    }

    fun detachHost(frame: FrameLayout) {
        if (host === frame) host = null
    }

    private fun destroyWebView(s: Session) {
        val w = s.webView ?: return
        if (attached === w) attached = null
        (w.parent as? ViewGroup)?.removeView(w)
        runCatching {
            w.stopLoading()
            w.loadUrl("about:blank")
            w.clearHistory()
            w.removeAllViews()
            w.destroy()
        }
        s.webView = null
    }

    // ------------------------------------------------------------- loading

    fun navigate(input: String) {
        val url = UrlUtils.toUrlOrSearch(input, app.settings.settings.searchEngine) ?: return
        val id = activeId ?: newTab(null)
        val s = sessions[id] ?: return
        if (s.info.isHome) {
            // The first navigation reuses the empty home tab.
            loadInSession(id, url)
        } else {
            loadInSession(id, url)
        }
    }

    fun loadUrl(url: String) {
        val id = activeId ?: newTab(null)
        loadInSession(id, url)
    }

    private fun loadInSession(id: Long, rawUrl: String) {
        val s = sessions[id] ?: return
        val url = UrlUtils.toUrlOrSearch(rawUrl, app.settings.settings.searchEngine) ?: return
        contextMenu = null
        if (s.info.isHome) s.homeInHistory = true
        s.info = s.info.copy(
            url = url,
            loading = true,
            progress = 0,
            errorCode = null,
            errorText = null,
            favicon = null,
            title = UrlUtils.titleFor(url),
        )
        publish()
        val w = s.webView ?: createWebView(id)
        s.loadRequested = true
        w.loadUrl(url)
        if (activeId == id) syncHost()
        saveState()
    }

    fun goHome() {
        val id = activeId ?: return
        val s = sessions[id] ?: return
        val home = homeUrl()
        if (home != null) {
            loadInSession(id, home)
            return
        }
        s.homeInHistory = false
        s.webView?.clearHistory()
        s.info = s.info.copy(
            url = null,
            title = "Homepage",
            loading = false,
            progress = 0,
            canGoBack = false,
            canGoForward = false,
            errorCode = null,
            errorText = null,
        )
        publish()
        syncHost()
        saveState()
    }

    fun goBack() {
        val id = activeId ?: return
        val s = sessions[id] ?: return
        val w = s.webView
        if (w != null && w.canGoBack()) {
            w.goBack()
        } else if (s.homeInHistory) {
            goHome()
        }
    }

    fun goForward() {
        val w = activeWebView() ?: return
        if (w.canGoForward()) w.goForward()
    }

    fun reload() {
        activeWebView()?.let { if (it.url != null) it.reload() }
    }

    fun stopLoading() {
        activeWebView()?.stopLoading()
    }

    fun openInNewTab(url: String, background: Boolean = false) {
        if (url.isBlank()) return
        val id = newTab(url, select = !background)
        if (background) notify("Opened in background")
    }

    fun shareUrl(url: String?, title: String?) {
        val target = url ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, target)
            putExtra(Intent.EXTRA_SUBJECT, title ?: "")
        }
        runCatching { activity.startActivity(Intent.createChooser(intent, "Share link")) }
    }

    fun copyUrl(url: String?) {
        if (url.isNullOrBlank()) return
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("URL", url))
        notify("Link copied to clipboard")
    }

    fun downloadUrl(url: String, mime: String? = null) {
        if (!url.startsWith("http")) {
            notify("This link cannot be downloaded")
            return
        }
        app.downloads.enqueue(url, null, null, mime)
        notify("Download started")
    }

    fun dismissContextMenu() {
        contextMenu = null
    }

    fun scrollToTop() {
        activeWebView()?.evaluateJavascript("window.scrollTo({top:0,behavior:'smooth'});", null)
    }

    fun scrollToBottom() {
        activeWebView()?.evaluateJavascript(
            "window.scrollTo({top:document.body.scrollHeight,behavior:'smooth'});",
            null,
        )
    }

    fun openExternal(url: String) {
        runCatching {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure { notify("No app can open this link") }
    }

    fun setDesktopMode(enabled: Boolean) {
        val id = activeId ?: return
        val s = sessions[id] ?: return
        s.info = s.info.copy(desktopMode = enabled)
        publish()
        s.webView?.let { w ->
            w.settings.userAgentString = if (enabled) desktopUa else null
            w.reload()
        }
        JumpToast.show(activity, if (enabled) "Desktop site enabled" else "Desktop site disabled")
    }

    fun setIncognito(enabled: Boolean) {
        val id = activeId ?: return
        val s = sessions[id] ?: return
        s.info = s.info.copy(incognito = enabled)
        s.webView?.clearHistory()
        publish()
        notify(if (enabled) "Incognito mode on" else "Incognito mode off")
        saveState()
    }

    fun toggleBookmark(): Boolean {
        val tab = activeTab ?: return false
        val url = tab.url ?: return false
        val exists = app.bookmarks.isBookmarked(url)
        val iconPath = if (exists) "" else saveBookmarkIcon(url, tab.favicon)
        app.bookmarks.toggle(tab.title, url, iconPath)
        notify(if (exists) "Bookmark removed" else "Bookmark added")
        return !exists
    }

    private fun saveBookmarkIcon(url: String, bitmap: android.graphics.Bitmap?): String {
        if (bitmap == null || bitmap.isRecycled) return ""
        return runCatching {
            val dir = java.io.File(app.filesDir, "bookmark_icons").apply { mkdirs() }
            val file = java.io.File(dir, "${url.hashCode()}.png")
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            file.absolutePath
        }.getOrDefault("")
    }

    fun saveTabIcon(url: String, bitmap: android.graphics.Bitmap?) = saveBookmarkIcon(url, bitmap)

    fun setAdBlock(enabled: Boolean) {
        app.settings.update { it.copy(adBlock = enabled) }
        reload()
    }

    fun setJavaScript(enabled: Boolean) {
        app.settings.update { it.copy(javaScript = enabled) }
        applySettingsToAll()
    }

    fun setLoadImages(enabled: Boolean) {
        app.settings.update { it.copy(loadImages = enabled) }
        applySettingsToAll()
    }

    fun setThirdPartyCookies(blocked: Boolean) {
        app.settings.update { it.copy(blockThirdPartyCookies = blocked) }
        applyCookiesToAll()
    }

    fun setTextZoom(zoom: Int) {
        app.settings.update { it.copy(textZoom = zoom) }
        applySettingsToAll()
    }

    fun applySettingsToAll() {
        val s = app.settings.settings
        sessions.values.forEach { session ->
            session.webView?.let { w ->
                w.settings.javaScriptEnabled = s.javaScript
                w.settings.blockNetworkImage = !s.loadImages
                w.settings.textZoom = s.textZoom
                applyDarkMode(w)
                CookieManager.getInstance().setAcceptThirdPartyCookies(w, !s.blockThirdPartyCookies)
            }
        }
    }

    fun applyCookiesToAll() {
        val blocked = app.settings.settings.blockThirdPartyCookies
        sessions.values.forEach { session ->
            session.webView?.let { CookieManager.getInstance().setAcceptThirdPartyCookies(it, !blocked) }
        }
    }

    fun applyDarkModeToAll() {
        sessions.values.forEach { session ->
            session.webView?.let { w ->
                applyDarkMode(w)
                // Sites pick their theme from `prefers-color-scheme`, so the
                // root colour scheme has to be updated as well or the page
                // keeps the theme it was loaded with.
                if (!w.url.isNullOrBlank() && w.url != "about:blank") syncColorScheme(w)
            }
        }
    }

    /**
     * Darkens web pages the way the reference does: through Chromium's own
     * algorithmic darkening, not by injecting CSS.
     *
     * Algorithmic darkening (Android 10+ via androidx.webkit, or the older
     * `FORCE_DARK_AUTO`/`FORCE_DARK_ON` flags) is performed by the engine after
     * layout, so it keeps images, gradients and contrast intact and only
     * inverts the areas a site left at default colours. The previous approach
     * forced `!important` backgrounds and text colours over every element,
     * which broke layouts and erased images on real sites.
     */
    private fun applyDarkMode(w: WebView) {
        val dark = isDarkTheme
        // Android 13 exposes algorithmic darkening on WebSettings directly.
        // On Android 10-12 the older force-dark flag is the same engine
        // feature under a different name. Using the platform API avoids
        // depending on androidx.webkit (which drags AppCompat in for a single
        // setting).
        if (Build.VERSION.SDK_INT >= 33) {
            runCatching { w.settings.isAlgorithmicDarkeningAllowed = dark }
        } else if (Build.VERSION.SDK_INT >= 29) {
            @Suppress("DEPRECATION")
            runCatching {
                w.settings.forceDark =
                    if (dark) WebSettings.FORCE_DARK_ON else WebSettings.FORCE_DARK_OFF
            }
        }
    }

    /**
     * Tells the renderer that this page should use a dark colour scheme.
     *
     * Sites that ship a dark theme do it with `@media (prefers-color-scheme:
     * dark)` and a `color-scheme` declaration. Setting the root `color-scheme`
     * makes that media query match, so those sites render their own proper
     * dark design; Chromium's algorithmic darkening then covers the sites that
     * have no dark theme of their own. Nothing here overrides colours, so
     * layout, images and contrast are left exactly as the site intended.
     */
    private fun syncColorScheme(w: WebView) {
        val scheme = if (isDarkTheme) "dark" else "light"
        w.evaluateJavascript(
            "(function(){var d=document.documentElement;" +
                "if(d){d.style.colorScheme='$scheme';}})()",
            null,
        )
    }

    var isDarkTheme: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            val changed = sessions.values.mapNotNull { it.webView }
            applyDarkModeToAll()
            // Algorithmic darkening is applied by the renderer, so an already
            // loaded page keeps its old appearance until it relayouts.
            changed.forEach { w ->
                if (w.url.isNullOrBlank() || w.url == "about:blank") return@forEach
                syncColorScheme(w)
            }
        }

    // --------------------------------------------------------------- webview

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(id: Long): WebView {
        val s = sessions[id] ?: error("missing session")
        val w = WebView(activity)
        val settings = app.settings.settings
        val baseUa = defaultUserAgent()
        s.webView = w
        w.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        )
        w.settings.apply {
            javaScriptEnabled = settings.javaScript
            domStorageEnabled = true
            databaseEnabled = true
            loadsImagesAutomatically = settings.loadImages
            blockNetworkImage = !settings.loadImages
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            mediaPlaybackRequiresUserGesture = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            textZoom = settings.textZoom
            userAgentString = if (s.info.desktopMode) desktopUa else baseUa
            allowFileAccess = true
            allowContentAccess = true
        }
        if (Build.VERSION.SDK_INT >= 26) {
            w.settings.safeBrowsingEnabled = true
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, !settings.blockThirdPartyCookies)
        applyDarkMode(w)
        w.webViewClient = ViaClient(id)
        w.webChromeClient = ViaChromeClient(id)
        w.setFindListener { active, total, _ -> findState = active to total }
        w.setDownloadListener { url, userAgent, disposition, mime, _ ->
            if (url.startsWith("blob:")) {
                notify("Blob downloads are not supported yet")
            } else {
                app.downloads.enqueue(url, userAgent, disposition, mime)
                notify("Download started")
            }
        }
        w.setOnLongClickListener {
            val hit = w.hitTestResult
            val extra = hit.extra
            when (hit.type) {
                WebView.HitTestResult.ANCHOR_TYPE,
                WebView.HitTestResult.SRC_ANCHOR_TYPE,
                -> {
                    if (!extra.isNullOrBlank()) {
                        contextMenu = WebContextMenu(linkUrl = extra, imageUrl = null, title = extra)
                        true
                    } else false
                }
                WebView.HitTestResult.IMAGE_TYPE,
                WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE,
                -> {
                    if (!extra.isNullOrBlank()) {
                        contextMenu = WebContextMenu(linkUrl = null, imageUrl = extra, title = "Image")
                        true
                    } else false
                }
                else -> false
            }
        }
        w.setOnCreateContextMenuListener(null)
        return w
    }

    private fun defaultUserAgent(): String = runCatching { WebSettings.getDefaultUserAgent(activity) }
        .getOrDefault("Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")

    private fun activeWebView(): WebView? {
        val id = activeId ?: return null
        return sessions[id]?.webView
    }

    fun getWebView(): WebView? = activeWebView()

    /** Resource sniffer runs while the panel is open only. */
    fun startResourceSniffing() {
        sniffedSnapshot = emptyList()
        networkSnapshot = emptyList()
        networkStart.clear()
        networkLogging = true
        panelsRevision++
    }

    fun stopResourceSniffing() {
        networkLogging = false
    }

    fun clearNetworkLog() {
        networkSnapshot = emptyList()
        sniffedSnapshot = emptyList()
        panelsRevision++
    }

    private fun noteRequest(url: String?, method: String?) {
        if (!networkLogging || url == null || method == null) return
        if (method != "GET" && method != "POST") return
        networkStart[url] = System.currentTimeMillis()
    }

    private fun noteResponse(url: String?, mime: String?, status: Int, blocked: Boolean = false) {
        if (!networkLogging || url == null) return
        val now = System.currentTimeMillis()
        val started = networkStart.remove(url) ?: now
        if (!url.startsWith("http")) return
        val entry = NetworkEntry(
            method = if (blocked) "BLOCK" else "GET",
            url = url,
            mime = mime.orEmpty(),
            status = status,
            startedAt = started,
            finishedAt = now,
            blocked = blocked,
        )
        val m = mime.orEmpty().lowercase()
        val sniffable = sniffableMime(m, url)
        if (sniffable) {
            val guess = resourceSize(status)
            if (sniffedSnapshot.none { it.url == url }) {
                sniffedSnapshot = (sniffedSnapshot + SniffedResource(url, m, guess)).takeLast(500)
            }
        }
        networkSnapshot = (listOf(entry) + networkSnapshot).take(400)
        // Only nudge the UI while a panel is actually showing the data;
        // otherwise page loads would recompose the chrome for nothing.
        if (networkLogging) panelsRevision++
    }

    /**
     * Runs the installed user scripts whose `@match` patterns cover [url].
     *
     * Each script is wrapped so one failing script cannot abort the rest, and
     * so its errors surface in the WebView console instead of silently
     * stopping the injection.
     */
    /** Host of the page that most recently finished loading, for autofill. */
    private var lastHost: String? = null

    /** Set when the current page contains a password field. */
    private var wasPasswordPage = false

    /** A submitted login waiting for the user to accept saving it. */
    var pendingLogin by mutableStateOf<PendingLogin?>(null)
        private set

    data class PendingLogin(val host: String, val username: String, val password: String)

    fun dismissPendingLogin() {
        pendingLogin = null
    }

    fun acceptPendingLogin() {
        val login = pendingLogin ?: return
        pendingLogin = null
        app.passwords.save(login.host, login.username, login.password)
        notify("Password saved")
    }

    private fun captureCandidate(raw: String?, sourceUrl: String) {
        if (!app.settings.settings.savePasswords) return
        val host = UrlUtils.host(sourceUrl)
        if (host.isBlank()) return
        val text = raw?.let { decoded ->
            runCatching { org.json.JSONTokener(decoded).nextValue() as? String }.getOrNull()
        } ?: return
        val json = runCatching { org.json.JSONObject(text) }.getOrNull() ?: return
        val user = json.optString("u").trim()
        val pass = json.optString("p")
        if (user.isBlank() || pass.isBlank()) return
        // Do not offer a login that is already stored unchanged.
        val existing = app.passwords.forHost(host).firstOrNull { it.username == user }
        if (existing != null && existing.password == pass) return
        pendingLogin = PendingLogin(host, user, pass)
    }

    /** Called by the UI when the user accepts saving a login. */
    fun saveLogin(host: String, username: String, password: String) {
        app.passwords.save(host, username, password)
    }

    /**
     * Offers to fill a saved login after a page loads.
     *
     * Autofill is deliberately conservative: it only fills logins the user
     * already chose to save for that exact host, and only when a password
     * field is present and empty.
     */
    private fun autofillIfPossible(view: WebView, url: String) {
        val host = UrlUtils.host(url)
        lastHost = host
        val logins = app.passwords.forHost(host)
        if (logins.isEmpty()) return
        val login = logins.first()
        view.evaluateJavascript(
            "(function(){var p=document.querySelector('input[type=password]');" +
                "return !!(p && !p.value);})()",
        ) { result ->
            if (result == "true") {
                view.evaluateJavascript(LoginAutofill.script(login.username, login.password), null)
            }
        }
    }

    fun injectUserScripts(view: WebView, url: String?) {
        if (url == null || !url.startsWith("http")) return
        val scripts = runCatching { app.scripts.forUrl(url) }.getOrDefault(emptyList())
        if (scripts.isEmpty()) return
        scripts.forEach { script ->
            val wrapped = "(function(){try{\n${script.source}\n}catch(e){" +
                "console.error('Jump script \"${script.name.replace("\"", "'")}\" failed:',e);}})();"
            view.evaluateJavascript(wrapped, null)
        }
    }

    private fun applyAdBlocking(view: WebView) {
        val host = UrlUtils.host(view.url)
        if (AdBlocker.isSiteException(host)) {
            view.evaluateJavascript(
                "(function(){var s=document.getElementById('via-ad-css');if(s)s.remove();})()",
                null,
            )
        } else if (app.settings.settings.adBlock) {
            injectCosmeticCss(view)
        }
    }

    private fun injectCosmeticCss(view: WebView) {
        view.evaluateJavascript(
            "(function(){if(!document.getElementById('via-ad-css')){var s=document.createElement('style');" +
                "s.id='via-ad-css';s.textContent=${JSONObject.quote(AdBlocker.cosmeticCss)};document.documentElement.appendChild(s);}})()",
            null,
        )
    }

    private fun sniffableMime(mime: String, url: String): Boolean {
        if (mime.startsWith("image/") || mime.startsWith("video/") || mime.startsWith("audio/")) return true
        if (mime.contains("javascript") || mime.contains("css") || mime.contains("font")) return true
        if (mime.startsWith("application/")) return true
        val ext = url.substringBefore('?').substringAfterLast('.', "").lowercase()
        return ext in setOf("jpg", "jpeg", "png", "gif", "webp", "svg", "ico", "mp4", "webm", "m3u8",
            "mp3", "ogg", "wav", "js", "css", "woff", "woff2", "ttf", "otf", "json", "wasm")
    }

    /** WebView does not expose response sizes; the panel shows the transfer as unavailable when anonymous. */
    private fun resourceSize(status: Int): Long = if (status in 200..299) -1L else 0L

    /** Persist exceptions to cosmetic ad blocking for the active site. */
    fun markSiteAsAdFree() {
        val host = activeTab?.url?.let { runCatching { Uri.parse(it).host }.getOrNull() }
        if (host.isNullOrBlank()) {
            notify("Open a page first")
            return
        }
        AdBlocker.addSiteException(host)
        activeWebView()?.let { applyAdBlocking(it) }
        notify("Ad blocking disabled for this site")
    }

    fun adBlockingException(): String? = activeTab?.url?.let { url ->
        runCatching { Uri.parse(url).host }.getOrNull()
    }

    private inner class ViaClient(private val id: Long) : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val url = request?.url?.toString() ?: return false
            return handleUrl(url)
        }

        @Deprecated("Deprecated in Java")
        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
            return url?.let { handleUrl(it) } ?: false
        }

        private fun handleUrl(url: String): Boolean {
            if (url.startsWith("http://") || url.startsWith("https://") ||
                url.startsWith("file://") || url.startsWith("content://") ||
                url.startsWith("about:") || url.startsWith("data:") || url.startsWith("blob:")
            ) return false
            if (url.startsWith("intent:")) {
                return try {
                    val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                    val fallback = intent.getStringExtra("browser_fallback_url")
                    runCatching { activity.startActivity(intent) }
                        .onFailure {
                            if (fallback != null) loadInSession(id, fallback) else notify("No app can open this link")
                        }
                    true
                } catch (e: Exception) {
                    true
                }
            }
            return try {
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                true
            } catch (e: ActivityNotFoundException) {
                notify("No app can open this link")
                true
            }
        }

        override fun shouldInterceptRequest(
            view: WebView?,
            request: WebResourceRequest?,
        ): WebResourceResponse? {
            val url = request?.url?.toString()
            noteRequest(url, request?.method)
            if (app.settings.settings.adBlock && request?.method == "GET" && AdBlocker.isBlocked(url)) {
                adBlockedCount += 1
                noteResponse(url, "blocked", 0, blocked = true)
                return WebResourceResponse(
                    "text/plain",
                    "utf-8",
                    204,
                    "No Content",
                    mapOf("Cache-Control" to "no-store"),
                    ByteArrayInputStream(ByteArray(0)),
                )
            }
            return null
        }

        override fun onReceivedHttpError(
            view: WebView?,
            request: WebResourceRequest?,
            errorResponse: WebResourceResponse?,
        ) {
            super.onReceivedHttpError(view, request, errorResponse)
            noteResponse(request?.url?.toString(), errorResponse?.mimeType, errorResponse?.statusCode ?: 0)
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
            super.onPageStarted(view, url, favicon)
            view?.let(::syncColorScheme)
            // document-start scripts have to be in before parsing continues.
            // Remember credentials the user submitted on the page they are
            // leaving, so they can be offered for saving after the redirect.
            if (view != null && url != null && wasPasswordPage) {
                view.evaluateJavascript(LoginAutofill.captureScript) { raw ->
                    captureCandidate(raw, url)
                }
            }
            if (view != null && url != null) {
                val early = runCatching { app.scripts.forUrl(url) }.getOrDefault(emptyList())
                    .filter { it.runAtStart }
                early.forEach { script ->
                    view.evaluateJavascript(
                        "(function(){try{${script.source}}catch(e){}})();",
                        null,
                    )
                }
            }
            if (url == null) return
            if (url == "about:blank") return
            update(id) {
                it.copy(
                    url = url,
                    loading = true,
                    progress = 0,
                    errorCode = null,
                    errorText = null,
                    secure = UrlUtils.isSecure(url),
                    favicon = favicon ?: it.favicon,
                    canGoBack = sessions[id]?.homeInHistory == true || view?.canGoBack() == true,
                )
            }
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            if (view == null || url == null) return
            val title = view.title
            update(id) {
                it.copy(
                    url = url,
                    title = if (!title.isNullOrBlank()) title else it.title,
                    loading = false,
                    progress = 100,
                    canGoBack = sessions[id]?.homeInHistory == true || view?.canGoBack() == true,
                    canGoForward = view.canGoForward(),
                    secure = UrlUtils.isSecure(url),
                )
            }

            syncColorScheme(view)
            if (app.settings.settings.savePasswords) {
                view.evaluateJavascript(
                    "!!document.querySelector('input[type=password]')",
                ) { result -> wasPasswordPage = result == "true" }
            }
            injectUserScripts(view, url)
            if (app.settings.settings.savePasswords) autofillIfPossible(view, url)
            if (app.settings.settings.doNotTrack) {
                view.evaluateJavascript(
                    "Object.defineProperty(navigator,'doNotTrack',{get:function(){return '1';},configurable:true});" +
                        "Object.defineProperty(navigator,'globalPrivacyControl',{get:function(){return true;},configurable:true});",
                    null,
                )
            }
            applyAdBlocking(view)
            if (sessions[id]?.info?.incognito != true) {
                app.history.record(view.title ?: "", url)
            }
            saveState()
        }

        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
            super.doUpdateVisitedHistory(view, url, isReload)
            if (view == null || url == null) return
            update(id) {
                it.copy(
                    url = url,
                    canGoBack = sessions[id]?.homeInHistory == true || view?.canGoBack() == true,
                    canGoForward = view.canGoForward(),
                    secure = UrlUtils.isSecure(url),
                )
            }
        }

        override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: WebResourceError?,
        ) {
            super.onReceivedError(view, request, error)
            if (request?.isForMainFrame != true) return
            val code = if (Build.VERSION.SDK_INT >= 23) error?.errorCode else null
            val text = if (Build.VERSION.SDK_INT >= 23) error?.description?.toString() else null
            update(id) {
                it.copy(
                    loading = false,
                    progress = 0,
                    errorCode = code ?: -1,
                    errorText = text ?: "Page could not be loaded",
                )
            }
        }

        override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
            handler?.cancel()
            notify("This site's certificate is not trusted")
            update(id) { it.copy(loading = false) }
        }
    }

    private inner class ViaChromeClient(private val id: Long) : WebChromeClient() {
        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            if (view == null) return
            update(id) { it.copy(progress = newProgress, loading = newProgress < 100) }
        }

        override fun onReceivedTitle(view: WebView?, title: String?) {
            if (view == null) return
            if (!title.isNullOrBlank()) {
                update(id) {
                    if (it.isHome) it else it.copy(title = title)
                }
            }
        }

        override fun onReceivedIcon(view: WebView?, icon: android.graphics.Bitmap?) {
            if (icon != null) update(id) { it.copy(favicon = icon) }
        }

        override fun onShowFileChooser(
            webView: WebView?,
            filePathCallback: ValueCallback<Array<Uri>>?,
            fileChooserParams: FileChooserParams?,
        ): Boolean {
            fileChooserCallback?.onReceiveValue(null)
            fileChooserCallback = filePathCallback
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = fileChooserParams?.acceptTypes?.firstOrNull { it.contains("/") }?.let {
                    if (it.startsWith(".")) "*/*" else it
                } ?: "*/*"
                if (fileChooserParams?.mode == FileChooserParams.MODE_OPEN_MULTIPLE) {
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
            }
            return try {
                launchFileChooser(intent)
                true
            } catch (e: Exception) {
                fileChooserCallback = null
                false
            }
        }

        override fun onPermissionRequest(request: PermissionRequest?) {
            if (request == null) return
            val granted = request.resources.filter { resource ->
                when (resource) {
                    PermissionRequest.RESOURCE_AUDIO_CAPTURE ->
                        ContextCompat.checkSelfPermission(activity, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                    PermissionRequest.RESOURCE_VIDEO_CAPTURE ->
                        ContextCompat.checkSelfPermission(activity, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                    PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID -> true
                    else -> false
                }
            }
            runCatching { if (granted.isEmpty()) request.deny() else request.grant(granted.toTypedArray()) }
        }

        override fun onGeolocationPermissionsShowPrompt(
            origin: String?,
            callback: GeolocationPermissions.Callback?,
        ) {
            val ok = ContextCompat.checkSelfPermission(activity, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            callback?.invoke(origin, ok, false)
        }

        override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
            if (result == null) return false
            AlertDialog.Builder(activity)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm() }
                .setOnCancelListener { result.cancel() }
                .show()
            return true
        }

        override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
            if (result == null) return false
            AlertDialog.Builder(activity)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm() }
                .setNegativeButton(android.R.string.cancel) { _, _ -> result.cancel() }
                .setOnCancelListener { result.cancel() }
                .show()
            return true
        }

        override fun onJsPrompt(
            view: WebView?,
            url: String?,
            message: String?,
            defaultValue: String?,
            result: android.webkit.JsPromptResult?,
        ): Boolean {
            if (result != null) {
                val input = EditText(activity).apply {
                    setText(defaultValue ?: "")
                    setSelection(text.length)
                }
                AlertDialog.Builder(activity)
                    .setMessage(message)
                    .setView(input)
                    .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm(input.text.toString()) }
                    .setNegativeButton(android.R.string.cancel) { _, _ -> result.cancel() }
                    .setOnCancelListener { result.cancel() }
                    .show()
                return true
            }
            return false
        }

        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
            if (view == null) return
            val decor = activity.window.decorView as FrameLayout
            decor.addView(
                view,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
            if (Build.VERSION.SDK_INT >= 30) {
                activity.window.setDecorFitsSystemWindows(false)
                activity.window.insetsController?.let {
                    it.hide(WindowInsets.Type.systemBars())
                    it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                activity.window.decorView.systemUiVisibility =
                    View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            }
            fullscreenCallback = callback
            fullscreenView = view
        }

        override fun onHideCustomView() {
            val decor = activity.window.decorView as FrameLayout
            fullscreenView?.let { decor.removeView(it) }
            fullscreenView = null
            fullscreenCallback?.onCustomViewHidden()
            fullscreenCallback = null
            if (Build.VERSION.SDK_INT >= 30) {
                activity.window.insetsController?.show(WindowInsets.Type.systemBars())
            } else {
                @Suppress("DEPRECATION")
                activity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            }
        }

        override fun onCreateWindow(
            view: WebView?,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: Message?,
        ): Boolean = false

        override fun onCloseWindow(window: WebView?) {
            // Popups are intentionally disabled, matching Via's default.
        }
    }

    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null

    fun hasFullscreen(): Boolean = fullscreenView != null

    fun exitFullscreen() {
        val view = fullscreenView ?: return
        (activity.window.decorView as? FrameLayout)?.removeView(view)
        fullscreenView = null
        runCatching { fullscreenCallback?.onCustomViewHidden() }
        fullscreenCallback = null
        if (Build.VERSION.SDK_INT >= 30) {
            activity.window.insetsController?.show(WindowInsets.Type.systemBars())
        } else {
            @Suppress("DEPRECATION")
            activity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    fun onFileChooserResult(resultCode: Int, data: Intent?) {
        val callback = fileChooserCallback ?: return
        fileChooserCallback = null
        if (resultCode != Activity.RESULT_OK) {
            callback.onReceiveValue(null)
            return
        }
        val uris = when {
            data?.clipData != null -> (0 until data.clipData!!.itemCount)
                .mapNotNull { data.clipData!!.getItemAt(it).uri }
                .toTypedArray()
            data?.data != null -> arrayOf(data.data!!)
            else -> emptyArray()
        }
        callback.onReceiveValue(uris)
    }

    // ---------------------------------------------------------------- extras

    fun findAll(query: String) {
        if (query.isBlank()) return
        if (query.length > 200) return
        activeWebView()?.findAllAsync(query)
    }

    fun findNext(forward: Boolean) {
        activeWebView()?.findNext(forward)
    }

    fun clearFind() {
        activeWebView()?.clearMatches()
        findState = 0 to 0
    }

    fun extractReader(callback: (ReaderData?) -> Unit) {
        val w = activeWebView()
        if (w == null) {
            callback(null)
            return
        }
        val js = """
            (function(){
              try {
                var nodes = document.querySelectorAll('article, main, [role="main"], #mw-content-text, .post, .article, .entry-content, #content, .markdown-body, .article-body, .story-body');
                var best = document.body;
                var bestScore = 0;
                for (var i=0;i<nodes.length;i++) {
                  var ps = nodes[i].querySelectorAll('p');
                  var t = '';
                  for (var j=0;j<ps.length;j++) t += (ps[j].innerText || '') + '\n\n';
                  var score = t.length + (ps.length * 120);
                  if (score > bestScore) { bestScore = score; best = nodes[i]; }
                }
                if (bestScore < 200) best = document.body;
                var junk = best.querySelectorAll('.navbox, .infobox, .reference, .references, .mw-editsection, .sidebar, .catlinks, table, script, style, noscript');
                for (var k=0;k<junk.length;k++) { junk[k].style.display = 'none'; }
                var parts = best.querySelectorAll('h1, h2, h3, p, li, blockquote, pre');
                var out = [];
                for (var m=0;m<parts.length;m++) {
                  var s = (parts[m].innerText || '').trim();
                  if (!s) continue;
                  if (s.length < 2) continue;
                  if (/^(edit|\[edit\])$/i.test(s)) continue;
                  out.push(s);
                }
                var text = out.join('\n\n').replace(/\n{3,}/g, '\n\n').trim();
                if (text.length > 120000) text = text.substring(0, 120000);
                var title = document.title || '';
                var byline = '';
                var meta = document.querySelector('meta[name="author"]');
                if (meta) byline = meta.getAttribute('content') || '';
                if (!byline) {
                  var a = document.querySelector('.author, .byline, [rel="author"]');
                  if (a) byline = (a.innerText || '').trim();
                }
                return JSON.stringify({title:title, byline:byline, text:text, url:location.href});
              } catch(e) { return null; }
            })()
        """.trimIndent()
        w.evaluateJavascript(js) { value ->
            val data = runCatching {
                if (value.isNullOrBlank() || value == "null") null
                else {
                    val decoded = JSONTokener(value).nextValue() as? String ?: return@runCatching null
                    val o = JSONObject(decoded)
                    val text = o.optString("text")
                    if (text.length < 120) null
                    else ReaderData(
                        title = o.optString("title", "Reader"),
                        byline = o.optString("byline"),
                        text = text,
                        url = o.optString("url"),
                    )
                }
            }.getOrNull()
            callback(data)
        }
    }

    fun viewSource(callback: (String) -> Unit) {
        val w = activeWebView()
        if (w == null) {
            callback("")
            return
        }
        w.evaluateJavascript("document.documentElement.outerHTML") { value ->
            val decoded = runCatching { JSONTokener(value).nextValue() as? String }.getOrNull()
            callback(decoded ?: value ?: "")
        }
    }

    /** Extracts readable text and returns it to the caller for TTS. */
    fun readPageAloud(callback: (ReaderData?) -> Unit) {
        extractReader(callback)
    }

    fun printPage() {
        val w = activeWebView() ?: return
        runCatching {
            val printManager = activity.getSystemService(Context.PRINT_SERVICE) as PrintManager
            val adapter = w.createPrintDocumentAdapter("Jump document")
            printManager.print("Jump", adapter, PrintAttributes.Builder().build())
        }.onFailure { notify("Printing is not available") }
    }

    fun savePage(type: String = "webarchive") {
        val w = activeWebView() ?: return
        val dir = java.io.File(activity.filesDir, "saved_pages").apply { mkdirs() }
        val name = (w.title ?: "page").replace(Regex("[^A-Za-z0-9._-]"), "_").take(48)
        val file = java.io.File(dir, "$name.${if (type == "webarchive") "mht" else "html"}")
        w.saveWebArchive(file.absolutePath, false) { path ->
            if (path != null) notify("Saved to ${file.name}") else notify("Could not save page")
        }
    }

    fun clearBrowsingData(clearHistory: Boolean, clearCookies: Boolean, clearCache: Boolean) {
        sessions.values.forEach {
            it.webView?.clearFormData()
            it.webView?.clearHistory()
            // Drop the loaded page so the cleared cookies and cache are not reused.
            it.webView?.loadUrl("about:blank")
        }
        if (clearCache) {
            sessions.values.forEach { it.webView?.clearCache(true) }
            WebStorage.getInstance().deleteAllData()
        }
        if (clearCookies) {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        }
        if (clearHistory) app.history.clear()
        CookieManager.getInstance().flush()
        notify("Browsing data cleared")
    }

    fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val raw = when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_WEB_SEARCH -> intent.getStringExtra(android.app.SearchManager.QUERY)
            else -> intent.dataString
        }?.trim()
        if (raw.isNullOrEmpty()) return
        val normalized = UrlUtils.toUrlOrSearch(raw, app.settings.settings.searchEngine) ?: return
        val current = activeTab
        if (current == null) {
            newTab(normalized)
        } else if (current.isHome) {
            loadInSession(current.id, normalized)
        } else {
            newTab(normalized)
        }
    }

    fun loadAfterPermission(permission: String, granted: Boolean) {
        // Hook for future site-permission prompts.
    }

    private fun saveState() {
        if (!app.settings.settings.restoreTabs) return
        val saved = sessions.values.filter { !it.info.incognito && it.info.url != null }
        val activeIndex = saved.indexOfFirst { it.info.id == activeId }.coerceAtLeast(0)
        app.tabs.save(
            saved.map {
                TabSnapshot(
                    url = it.info.url ?: "",
                    title = it.info.title,
                    incognito = false,
                    desktopMode = it.info.desktopMode,
                )
            },
            activeIndex,
        )
    }

    private fun restoreTabs() {
        if (!app.settings.settings.restoreTabs) {
            newTab(null, incognito = false)
            return
        }
        val (snapshots, activeIndex) = app.tabs.load()
        val restorable = snapshots.filter { it.url.startsWith("http") || it.url.startsWith("file") }
        if (restorable.isEmpty()) {
            newTab(null, incognito = false)
            return
        }
        restorable.forEachIndexed { index, snapshot ->
            val id = ids.incrementAndGet()
            sessions[id] = Session(
                TabInfo(
                    id = id,
                    url = snapshot.url,
                    title = snapshot.title.ifBlank { UrlUtils.titleFor(snapshot.url) },
                    desktopMode = snapshot.desktopMode,
                )
            )
            if (index == activeIndex.coerceIn(0, restorable.lastIndex)) activeId = id
        }
        activeId = activeId ?: sessions.keys.firstOrNull()
        publish()
        syncHost()
    }

    fun onPause() {
        sessions.values.forEach { it.webView?.onPause() }
        saveState()
    }

    fun onResume() {
        sessions.values.forEach { it.webView?.onResume() }
        app.downloads.refresh()
    }

    fun onDestroy() {
        saveState()
        sessions.values.forEach { destroyWebView(it) }
        sessions.clear()
        publish()
    }

    fun notify(message: CharSequence) {
        JumpToast.show(activity, message.toString())
    }
}

private fun Boolean?.orFalse(): Boolean = this ?: false

