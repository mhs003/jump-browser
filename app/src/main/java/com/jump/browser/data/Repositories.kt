package com.jump.browser.data

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

private const val PREFS = "via_clone_prefs"

class SettingsRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    var settings by mutableStateOf(load())
        private set

    private fun load(): AppSettings {
        val defaults = AppSettings()
        if (!prefs.contains("settings_json")) return defaults
        return runCatching {
            val o = JSONObject(prefs.getString("settings_json", "{}") ?: "{}")
            AppSettings(
                searchEngineId = o.optString("searchEngineId", defaults.searchEngineId),
                nightMode = o.optBoolean("nightMode", defaults.nightMode),
                savePasswords = o.optBoolean("savePasswords", defaults.savePasswords),
                ai = ChatSettings.fromJson(o),
                orientation = o.optString("orientation", defaults.orientation),
                fullscreen = o.optBoolean("fullscreen", defaults.fullscreen),
                customHomeCss = o.optString("customHomeCss", defaults.customHomeCss),
                homepageLogoPath = o.optString("homepageLogoPath", defaults.homepageLogoPath),
                homepageBackgroundPath = o.optString("homepageBackgroundPath", defaults.homepageBackgroundPath),
                homepageBackgroundOpacity = o.optInt("homepageBackgroundOpacity", defaults.homepageBackgroundOpacity),
                homepageSearchBar = o.optBoolean("homepageSearchBar", defaults.homepageSearchBar),
                homepageFavoriteColor = o.optBoolean("homepageFavoriteColor", defaults.homepageFavoriteColor),
                toolbarPosition = o.optString("toolbarPosition", defaults.toolbarPosition),
                userAgent = o.optString("userAgent", defaults.userAgent),
                toolbarLayout = o.optString("toolbarLayout", defaults.toolbarLayout),
                toolbarSearch = o.optBoolean("toolbarSearch", defaults.toolbarSearch),
                customizeMenu = runCatching {
                    val arr = o.optJSONArray("customizeMenu") ?: return@runCatching emptyList<String>()
                    (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotEmpty() }
                }.getOrDefault(emptyList()),
                customSearchUrl = o.optString("customSearchUrl", defaults.customSearchUrl),
                homepageUrl = o.optString("homepageUrl", defaults.homepageUrl),
                darkMode = runCatching { DarkMode.valueOf(o.optString("darkMode", "LIGHT")) }
                    .getOrDefault(DarkMode.LIGHT),
                adBlock = o.optBoolean("adBlock", defaults.adBlock),
                blockThirdPartyCookies = o.optBoolean("blockThirdPartyCookies", defaults.blockThirdPartyCookies),
                javaScript = o.optBoolean("javaScript", defaults.javaScript),
                loadImages = o.optBoolean("loadImages", defaults.loadImages),
                doNotTrack = o.optBoolean("doNotTrack", defaults.doNotTrack),
                desktopMode = o.optBoolean("desktopMode", defaults.desktopMode),
                restoreTabs = o.optBoolean("restoreTabs", defaults.restoreTabs),
                searchSuggestions = o.optBoolean("searchSuggestions", defaults.searchSuggestions),
                readerMode = o.optBoolean("readerMode", defaults.readerMode),
                textZoom = o.optInt("textZoom", defaults.textZoom),
                incognitoDefault = o.optBoolean("incognitoDefault", defaults.incognitoDefault),
                confirmExit = o.optBoolean("confirmExit", defaults.confirmExit),
            )
        }.getOrDefault(defaults)
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(settings)
        settings = next
        save(next)
    }

    /** The settings as stored, for a backup file. */
    fun exportJson(): JSONObject = settingsJson(settings)

    /**
     * Applies settings from a backup. Device-specific paths (a custom logo
     * stored under this app's files dir) are kept as they are, and the file is
     * re-read through the normal loader so defaults and migrations apply.
     */
    fun restoreJson(o: JSONObject) {
        val current = settingsJson(settings)
        Backup.deviceOnlySettings.forEach { key ->
            current.optString(key).takeIf { it.isNotEmpty() }?.let { current.put(key, it) }
        }
        o.keys().forEach { key ->
            if (key !in Backup.deviceOnlySettings) current.put(key, o.get(key))
        }
        prefs.edit().putString("settings_json", current.toString()).apply()
        settings = load()
    }

    private fun settingsJson(s: AppSettings): JSONObject {
        val o = JSONObject()
            .put("searchEngineId", s.searchEngineId)
            .put("nightMode", s.nightMode)
            .put("savePasswords", s.savePasswords)
            .put("fullscreen", s.fullscreen)
            .put("customHomeCss", s.customHomeCss)
            .put("homepageLogoPath", s.homepageLogoPath)
            .put("homepageBackgroundPath", s.homepageBackgroundPath)
            .put("homepageBackgroundOpacity", s.homepageBackgroundOpacity)
            .put("homepageSearchBar", s.homepageSearchBar)
            .put("homepageFavoriteColor", s.homepageFavoriteColor)
            .put("toolbarPosition", s.toolbarPosition)
            .put("userAgent", s.userAgent)
            .put("toolbarLayout", s.toolbarLayout)
            .put("toolbarSearch", s.toolbarSearch)
            .put("customizeMenu", org.json.JSONArray(s.customizeMenu))
            .put("customSearchUrl", s.customSearchUrl)
            .put("homepageUrl", s.homepageUrl)
            .put("darkMode", s.darkMode.name)
            .put("adBlock", s.adBlock)
            .put("blockThirdPartyCookies", s.blockThirdPartyCookies)
            .put("javaScript", s.javaScript)
            .put("loadImages", s.loadImages)
            .put("doNotTrack", s.doNotTrack)
            .put("desktopMode", s.desktopMode)
            .put("restoreTabs", s.restoreTabs)
            .put("searchSuggestions", s.searchSuggestions)
            .put("readerMode", s.readerMode)
            .put("textZoom", s.textZoom)
            .put("incognitoDefault", s.incognitoDefault)
            .put("confirmExit", s.confirmExit)
        ChatSettings.writeTo(o, s.ai)
        o.put("orientation", s.orientation)
        return o
    }

    private fun save(s: AppSettings) {
        prefs.edit().putString("settings_json", settingsJson(s).toString()).apply()
    }
}

class BookmarkRepository(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val idGen = AtomicLong(System.currentTimeMillis())
    var bookmarks by mutableStateOf<List<Bookmark>>(emptyList())
        private set

    /** True once the stored list has been read; writes wait for it. */
    @Volatile
    private var loaded = false

    init {
        // Parsed off the main thread: this used to run inside
        // Application.onCreate and delayed the first frame. Anything added
        // before the load finishes is merged rather than dropped.
        scope.launch {
            val stored = Bookmark.listFromJson(prefs.getString("bookmarks", "[]"))
            synchronized(this@BookmarkRepository) {
                val pending = bookmarks
                bookmarks = stored + pending.filter { p -> stored.none { it.url == p.url } }
                loaded = true
            }
        }
    }

    fun add(title: String, url: String, folder: String = "", iconPath: String = "") {
        if (url.isBlank()) return
        synchronized(this) {
            if (bookmarks.any { it.url == url }) return
            val item = Bookmark(
                idGen.incrementAndGet(),
                title.ifBlank { url },
                url,
                folder = folder,
                iconPath = iconPath,
            )
            bookmarks = bookmarks + item
        }
        persist()
    }

    fun remove(id: Long) {
        bookmarks = bookmarks.filterNot { it.id == id }
        persist()
    }

    fun removeUrl(url: String) {
        bookmarks = bookmarks.filterNot { it.url == url }
        persist()
    }

    fun update(id: Long, title: String, url: String, folder: String) {
        bookmarks = bookmarks.map { if (it.id == id) it.copy(title = title, url = url, folder = folder) else it }
        persist()
    }

    fun toggle(title: String, url: String, iconPath: String = "") {
        val existing = bookmarks.firstOrNull { it.url == url }
        if (existing != null) remove(existing.id) else add(title, url, iconPath = iconPath)
    }

    fun isBookmarked(url: String) = bookmarks.any { it.url == url }

    /** Every distinct folder name currently in use, in first-seen order. */
    fun folders(): List<String> =
        bookmarks.map { it.folder.trim() }.filter { it.isNotEmpty() }.distinct().sorted()

    fun inFolder(folder: String): List<Bookmark> =
        if (folder.isBlank()) bookmarks.filter { it.folder.isBlank() }
        else bookmarks.filter { it.folder == folder }

    /** Netscape bookmark file: the format every other browser can import. */
    fun exportHtml(): String {
        fun escape(value: String) = value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

        fun entry(b: Bookmark) =
            """        <DT><A HREF="${escape(b.url)}" ADD_DATE="${b.createdAt / 1000}">${escape(b.title)}</A>"""

        val loose = bookmarks.filter { it.folder.isBlank() }.joinToString("\n", transform = ::entry)
        val foldersHtml = folders().joinToString("\n") { folder ->
            val items = inFolder(folder).joinToString("\n", transform = ::entry)
            "    <DT><H3>${escape(folder)}</H3>\n" +
                "    <DL><p>\n" + items + "\n    </DL><p>"
        }
        return """<!DOCTYPE NETSCAPE-Bookmark-file-1>
<META HTTP-EQUIV="Content-Type" CONTENT="text/html; charset=UTF-8">
<TITLE>Bookmarks</TITLE>
<H1>Bookmarks</H1>
<DL><p>
$loose
$foldersHtml
</DL><p>
"""
    }

    /**
     * Parses a Netscape bookmark file and merges it in.
     *
     * Duplicate URLs are skipped, matching what browsers do on import.
     * Returns how many bookmarks were added.
     */
    fun importHtml(html: String): Int {
        val parsed = NetscapeBookmarks.parse(html, idGen::incrementAndGet)
        var count = 0
        synchronized(this) {
            val existing = bookmarks.map { it.url }.toMutableSet()
            val fresh = parsed.filter { existing.add(it.url) }
            if (fresh.isNotEmpty()) bookmarks = bookmarks + fresh
            count = fresh.size
        }
        if (count > 0) persist()
        return count
    }

    /** Merges a restored list, keeping existing entries and skipping duplicates. */
    fun mergeAll(items: List<Bookmark>): Int {
        var added = 0
        synchronized(this) {
            val urls = bookmarks.map { it.url }.toMutableSet()
            val fresh = items.filter { urls.add(it.url) }
            added = fresh.size
            if (fresh.isNotEmpty()) bookmarks = bookmarks + fresh
        }
        if (added > 0) persist()
        return added
    }

    /** Replaces the stored list, used by import with "replace" semantics. */
    fun replaceAll(items: List<Bookmark>) {
        synchronized(this) { bookmarks = items }
        persist()
    }

    private fun persist() {
        val snapshot = bookmarks
        scope.launch {
            runCatching {
                prefs.edit().putString("bookmarks", Bookmark.listToJson(snapshot)).apply()
            }
        }
    }
}

class HistoryRepository(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val idGen = AtomicLong(System.currentTimeMillis())
    var entries by mutableStateOf<List<HistoryEntry>>(emptyList())
        private set

    init {
        // Parsing a few thousand entries is not free; doing it during
        // Application.onCreate delayed first frame. Load it off the main
        // thread and publish when ready, merging anything recorded meanwhile.
        scope.launch {
            val stored = HistoryEntry.listFromJson(prefs.getString("history", "[]"))
            synchronized(this@HistoryRepository) {
                val pending = entries
                entries = (pending + stored.filter { s -> pending.none { it.id == s.id } })
                    .sortedByDescending { it.visitedAt }
                    .take(3000)
            }
        }
    }

    fun record(title: String, url: String, incognito: Boolean = false) {
        if (incognito || !url.startsWith("http")) return
        val now = System.currentTimeMillis()
        synchronized(this) {
            val latest = entries.firstOrNull()
            entries = if (latest != null && latest.url == url && now - latest.visitedAt < 120_000) {
                listOf(latest.copy(title = title.ifBlank { latest.title }, visitedAt = now)) + entries.drop(1)
            } else {
                (listOf(HistoryEntry(idGen.incrementAndGet(), title.ifBlank { url }, url, now)) + entries)
                    .take(3000)
            }
        }
        persist()
    }

    fun delete(id: Long) {
        entries = entries.filterNot { it.id == id }
        persist()
    }

    fun clear() {
        entries = emptyList()
        persist()
    }

    fun clearSince(timestamp: Long) {
        entries = entries.filter { it.visitedAt < timestamp }
        persist()
    }

    fun search(query: String): List<HistoryEntry> {
        if (query.isBlank()) return entries
        val q = query.lowercase()
        return entries.filter { it.title.lowercase().contains(q) || it.url.lowercase().contains(q) }
    }

    /** Merges restored entries, skipping ones already present. */
    fun mergeAll(items: List<HistoryEntry>): Int {
        var added = 0
        synchronized(this) {
            val ids = entries.map { it.id }.toMutableSet()
            val fresh = items.filter { ids.add(it.id) }
            added = fresh.size
            if (fresh.isNotEmpty()) {
                entries = (entries + fresh).sortedByDescending { it.visitedAt }.take(3000)
            }
        }
        if (added > 0) persist()
        return added
    }

    private fun persist() {
        // Serialising thousands of entries and writing them through
        // SharedPreferences must not happen on the main thread after every
        // page visit: it is the single biggest cost of browsing here.
        val snapshot = entries
        scope.launch {
            runCatching {
                prefs.edit().putString("history", HistoryEntry.listToJson(snapshot)).apply()
            }
        }
    }
}

class ScriptRepository(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val idGen = AtomicLong(System.currentTimeMillis())
    var scripts by mutableStateOf<List<UserScript>>(emptyList())
        private set

    /** True once the stored list has been read. */
    @Volatile
    private var loaded = false

    init {
        scope.launch {
            val stored = UserScriptJson.listFromJson(prefs.getString("userscripts", "[]"))
            synchronized(this@ScriptRepository) {
                // Merge rather than replace: a script added while the read was
                // in flight must not be dropped, and the merged result has to be
                // written back or the add would be lost on the next launch.
                val pending = scripts
                val merged = stored + pending.filter { p -> stored.none { it.id == p.id } }
                scripts = merged
                loaded = true
                if (pending.isNotEmpty()) {
                    prefs.edit().putString("userscripts", UserScriptJson.listToJson(merged)).apply()
                }
            }
        }
    }

    /** Adds a script from its source text; the metadata block names it. */
    fun add(source: String): UserScript {
        val script = UserScript.parse(source, idGen.incrementAndGet())
        synchronized(this) {
            scripts = scripts + script
            // Write while holding the lock so the async initial load cannot
            // read the old file and persist it over this script.
            prefs.edit().putString("userscripts", UserScriptJson.listToJson(scripts)).apply()
        }
        return script
    }

    fun remove(id: Long) {
        synchronized(this) { scripts = scripts.filterNot { it.id == id } }
        persist()
    }

    fun setEnabled(id: Long, enabled: Boolean) {
        synchronized(this) {
            scripts = scripts.map { if (it.id == id) it.copy(enabled = enabled) else it }
        }
        persist()
    }

    /** Scripts that should run for [url], in install order. */
    fun forUrl(url: String?): List<UserScript> {
        if (url == null) return emptyList()
        return scripts.filter { it.appliesTo(url) }
    }

    private fun persist() {
        val snapshot = scripts
        scope.launch {
            runCatching {
                prefs.edit().putString("userscripts", UserScriptJson.listToJson(snapshot)).apply()
            }
        }
    }
}

class TabStore(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): Pair<List<TabSnapshot>, Int> {
        val raw = prefs.getString("tabs", null)
        val list = TabSnapshot.listFromJson(raw)
        val active = prefs.getInt("active_tab", 0)
        return list to active
    }

    fun save(tabs: List<TabSnapshot>, active: Int) {
        prefs.edit()
            .putString("tabs", TabSnapshot.listToJson(tabs))
            .putInt("active_tab", active)
            .apply()
    }
}

enum class DownloadState { PENDING, RUNNING, PAUSED, COMPLETE, FAILED }

data class DownloadTask(
    val id: Long,
    val title: String,
    val url: String,
    val mime: String,
    val localUri: String?,
    val bytes: Long,
    val total: Long,
    val state: DownloadState,
    val createdAt: Long,
) {
    val fraction: Float
        get() = if (total > 0) (bytes.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
    val sizeText: String
        get() = when {
            total <= 0 -> ""
            state == DownloadState.COMPLETE -> formatBytes(total)
            else -> "${formatBytes(bytes)} / ${formatBytes(total)}"
        }

    companion object {
        fun formatBytes(value: Long): String {
            if (value < 1024) return "$value B"
            val kb = value / 1024.0
            if (kb < 1024) return String.format("%.1f KB", kb)
            val mb = kb / 1024.0
            if (mb < 1024) return String.format("%.1f MB", mb)
            return String.format("%.2f GB", mb / 1024.0)
        }
    }
}

private data class DownloadMeta(
    val id: Long,
    val url: String,
    val title: String,
    val mime: String,
    val createdAt: Long,
)

class DownloadRepository(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    var tasks by mutableStateOf<List<DownloadTask>>(emptyList())
        private set
    private var running = false

    private fun loadMeta(): MutableList<DownloadMeta> = runCatching {
        val arr = JSONArray(prefs.getString("downloads", "[]") ?: "[]")
        MutableList(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            DownloadMeta(
                o.optLong("id"), o.optString("url"), o.optString("title"),
                o.optString("mime"), o.optLong("createdAt"),
            )
        }
    }.getOrDefault(mutableListOf())

    private fun saveMeta(items: List<DownloadMeta>) {
        val arr = JSONArray()
        items.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("url", it.url).put("title", it.title)
                    .put("mime", it.mime).put("createdAt", it.createdAt)
            )
        }
        prefs.edit().putString("downloads", arr.toString()).apply()
    }

    fun enqueue(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mime: String?,
        suggestedName: String? = null,
    ): Long? {
        if (!url.startsWith("http")) return null
        val dispositionName = contentDisposition
            ?.takeIf { it.contains("filename", ignoreCase = true) }
            ?.let { guessName(it) }
        val name = suggestedName ?: dispositionName ?: guessName(url)
        val safeName = sanitiseName(name)
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(safeName)
            .setDescription(url)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
        if (Build.VERSION.SDK_INT >= 29) {
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, safeName)
        } else {
            request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, safeName)
        }
        if (!userAgent.isNullOrBlank()) request.addRequestHeader("User-Agent", userAgent)
        if (!contentDisposition.isNullOrBlank()) request.addRequestHeader("Content-Disposition", contentDisposition)
        if (!mime.isNullOrBlank()) request.setMimeType(mime)
        val id = runCatching { manager.enqueue(request) }.getOrNull() ?: return null
        val meta = loadMeta()
        meta.add(0, DownloadMeta(id, url, safeName, mime ?: "", System.currentTimeMillis()))
        saveMeta(meta)
        startPolling()
        return id
    }

    fun startPolling() {
        if (running) return
        running = true
        scope.launch {
            while (running) {
                refresh()
                delay(900)
            }
        }
    }

    fun refresh() {
        val meta = loadMeta()
        val ids = meta.map { it.id }.toLongArray()
        if (ids.isEmpty()) {
            tasks = emptyList()
            return
        }
        val out = mutableListOf<DownloadTask>()
        runCatching {
            manager.query(DownloadManager.Query().setFilterById(*ids)).use { c: Cursor ->
                while (c.moveToNext()) {
                    val id = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
                    val info = meta.firstOrNull { it.id == id }
                    val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val bytes = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    val localUri = c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
                    val uri = if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        runCatching { manager.getUriForDownloadedFile(id)?.toString() }.getOrNull() ?: localUri
                    } else localUri
                    val mime = c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_MEDIA_TYPE))
                    out += DownloadTask(
                        id = id,
                        title = info?.title ?: c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)) ?: "Download",
                        url = info?.url ?: "",
                        mime = mime ?: info?.mime ?: "",
                        localUri = uri,
                        bytes = bytes.coerceAtLeast(0),
                        total = total,
                        state = when (status) {
                            DownloadManager.STATUS_PENDING -> DownloadState.PENDING
                            DownloadManager.STATUS_RUNNING -> DownloadState.RUNNING
                            DownloadManager.STATUS_PAUSED -> DownloadState.PAUSED
                            DownloadManager.STATUS_SUCCESSFUL -> DownloadState.COMPLETE
                            else -> DownloadState.FAILED
                        },
                        createdAt = info?.createdAt ?: 0L,
                    )
                }
            }
        }
        tasks = out.sortedByDescending { it.createdAt }
        if (out.none { it.state == DownloadState.PENDING || it.state == DownloadState.RUNNING || it.state == DownloadState.PAUSED }) {
            running = false
        }
    }

    fun remove(id: Long) {
        runCatching { manager.remove(id) }
        saveMeta(loadMeta().filterNot { it.id == id })
        refresh()
    }

    fun clearCompleted() {
        val meta = loadMeta()
        tasks.filter { it.state == DownloadState.COMPLETE || it.state == DownloadState.FAILED }.forEach {
            runCatching { manager.remove(it.id) }
        }
        saveMeta(meta.filterNot { m -> tasks.any { it.id == m.id && (it.state == DownloadState.COMPLETE || it.state == DownloadState.FAILED) } })
        refresh()
    }

    private fun guessName(dispositionOrUrl: String): String {
        val decoded = Uri.decode(dispositionOrUrl)
        val filename = Regex("filename\\*?=(?:UTF-8''|\"?)([^\";]+)", RegexOption.IGNORE_CASE)
            .find(decoded)?.groupValues?.getOrNull(1)
        if (!filename.isNullOrBlank()) return filename
        val path = runCatching { Uri.parse(dispositionOrUrl).lastPathSegment }.getOrNull()
        if (!path.isNullOrBlank() && path != "/") return path
        return "download-${System.currentTimeMillis()}.bin"
    }

    private fun sanitiseName(name: String): String {
        val cleaned = name.substringBefore('?').substringAfterLast('/')
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim()
        return cleaned.ifBlank { "download-${System.currentTimeMillis()}.bin" }.take(96)
    }
}

class AppContainer(context: Context) {
    val scope = CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)

    // Constructed on first use rather than all at once: each repository opens
    // SharedPreferences and parses JSON, and doing that for every one of them
    // inside Application.onCreate was a measurable part of cold start. The
    // only repository needed to draw the first frame is `settings`.
    val settings: SettingsRepository by lazy { SettingsRepository(context) }
    val bookmarks: BookmarkRepository by lazy { BookmarkRepository(context, scope) }
    val history: HistoryRepository by lazy { HistoryRepository(context, scope) }
    val downloads: DownloadRepository by lazy { DownloadRepository(context, scope) }
    val tabs: TabStore by lazy { TabStore(context) }
    val scripts: ScriptRepository by lazy { ScriptRepository(context, scope) }
    val passwords: PasswordRepository by lazy { PasswordRepository(context, scope) }
    val filesDir: File = context.filesDir
}
