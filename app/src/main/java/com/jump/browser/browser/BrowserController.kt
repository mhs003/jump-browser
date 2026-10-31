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
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.jump.browser.data.AppContainer
import com.jump.browser.data.TabSnapshot
import com.jump.browser.data.TabStore
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicLong

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
    private class Session(var info: TabInfo, var webView: WebView? = null)

    private val sessions = LinkedHashMap<Long, Session>()
    private val ids = AtomicLong(1)
    private var host: FrameLayout? = null
    private var attached: WebView? = null
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null

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

    private fun session(id: Long): Session? = sessions[id]

    private fun update(id: Long, transform: (TabInfo) -> TabInfo) {
        val s = sessions[id] ?: return
        s.info = transform(s.info)
        publish()
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
            syncHost()
            if (target != null) loadInSession(id, target)
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
            if (desired.url == null) {
                active?.info?.url?.let(desired::loadUrl)
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
        s.info = s.info.copy(
            url = url,
            loading = true,
            progress = 0,
            errorCode = null,
            errorText = null,
            title = if (s.info.title.isBlank() || s.info.title == "Homepage") UrlUtils.titleFor(url) else s.info.title,
        )
        publish()
        val w = s.webView ?: createWebView(id)
        if (activeId == id) syncHost()
        w.loadUrl(url)
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
        val w = activeWebView() ?: return
        if (w.canGoBack()) w.goBack()
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
        Toast.makeText(activity, if (enabled) "Desktop site enabled" else "Desktop site disabled", Toast.LENGTH_SHORT).show()
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
        app.bookmarks.toggle(tab.title, url)
        notify(if (exists) "Bookmark removed" else "Bookmark added")
        return !exists
    }

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
        sessions.values.forEach { it.webView?.let(::applyDarkMode) }
    }

    private fun applyDarkMode(w: WebView) {
        if (Build.VERSION.SDK_INT >= 29) {
            @Suppress("DEPRECATION")
            w.settings.forceDark = if (isDarkTheme) WebSettings.FORCE_DARK_ON else WebSettings.FORCE_DARK_OFF
        } else {
            val css = if (isDarkTheme) {
                "html{filter:invert(1) hue-rotate(180deg);background:#111!important}img,video,picture,canvas,svg{filter:invert(1) hue-rotate(180deg)!important}"
            } else ""
            w.evaluateJavascript(
                "(function(){var s=document.getElementById('via-dark-css');" +
                    "if(!s){s=document.createElement('style');s.id='via-dark-css';document.head&&document.head.appendChild(s);}" +
                    "s.textContent=${JSONObject.quote(css)};})()",
                null,
            )
        }
    }

    var isDarkTheme: Boolean = false
        set(value) {
            field = value
            applyDarkModeToAll()
        }

    // --------------------------------------------------------------- webview

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(id: Long): WebView {
        val s = sessions[id] ?: error("missing session")
        val w = WebView(activity)
        val settings = app.settings.settings
        val prefs = settings
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
            supportZoom = true
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
            if (app.settings.settings.adBlock && request?.method == "GET" && AdBlocker.isBlocked(url)) {
                adBlockedCount += 1
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

        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
            super.onPageStarted(view, url, favicon)
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
                    canGoBack = view.canGoBack(),
                    canGoForward = view.canGoForward(),
                    secure = UrlUtils.isSecure(url),
                )
            }
            if (app.settings.settings.adBlock) {
                view.evaluateJavascript(
                    "(function(){if(!document.getElementById('via-ad-css')){var s=document.createElement('style');" +
                        "s.id='via-ad-css';s.textContent=${JSONObject.quote(AdBlocker.cosmeticCss)};document.documentElement.appendChild(s);}})()",
                    null,
                )
            }
            if (!sessions[id]?.info?.incognito.orFalse()) {
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
                    canGoBack = view.canGoBack(),
                    canGoForward = view.canGoForward(),
                    secure = UrlUtils.isSecure(url),
                )
            }
        }

        override fun onReceivedIcon(view: WebView?, icon: android.graphics.Bitmap?) {
            if (icon != null) update(id) { it.copy(favicon = icon) }
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
            result: JsResult?,
        ): Boolean {
            if (result is android.webkit.JsPromptResult) {
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
        if (fullscreenView != null) {
            fullscreenView = null
            activity.window.insetsController?.show(WindowInsets.Type.systemBars())
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
                var nodes = document.querySelectorAll('article, main, [role="main"], .post, .article, .entry-content, #content, .markdown-body');
                var best = document.body;
                var bestScore = 0;
                for (var i=0;i<nodes.length;i++) {
                  var t = nodes[i].innerText || '';
                  var score = t.length + (nodes[i].querySelectorAll('p').length * 250);
                  if (score > bestScore) { bestScore = score; best = nodes[i]; }
                }
                var text = (best.innerText || '').replace(/\n{3,}/g, '\n\n').trim();
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

    fun printPage() {
        val w = activeWebView() ?: return
        val printManager = activity.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val adapter = w.createPrintDocumentAdapter("Via Clone document")
        printManager.print("Via Clone", adapter, PrintAttributes.Builder().build())
    }

    fun savePage(type: String = "webarchive") {
        val w = activeWebView() ?: return
        val dir = java.io.File(activity.filesDir, "saved_pages").apply { mkdirs() }
        val name = (w.title ?: "page").replace(Regex("[^A-Za-z0-9._-]"), "_").take(48)
        val file = java.io.File(dir, "$name.${if (type == "webarchive") "mht" else "html"}")
        w.saveWebArchive(file.absolutePath) { path ->
            if (path != null) notify("Saved to ${file.name}") else notify("Could not save page")
        }
    }

    fun clearBrowsingData(clearHistory: Boolean, clearCookies: Boolean, clearCache: Boolean) {
        sessions.values.forEach {
            it.webView?.clearFormData()
            it.webView?.clearHistory()
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
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
    }
}

private fun Boolean?.orFalse(): Boolean = this ?: false

