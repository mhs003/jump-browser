package com.jump.browser.data

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
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

    private fun save(s: AppSettings) {
        val o = JSONObject()
            .put("searchEngineId", s.searchEngineId)
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
        prefs.edit().putString("settings_json", o.toString()).apply()
    }
}

class BookmarkRepository(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val idGen = AtomicLong(System.currentTimeMillis())
    var bookmarks by mutableStateOf(Bookmark.listFromJson(prefs.getString("bookmarks", "[]")))
        private set

    fun add(title: String, url: String, folder: String = "", iconPath: String = "") {
        if (url.isBlank()) return
        if (bookmarks.any { it.url == url }) return
        val item = Bookmark(idGen.incrementAndGet(), title.ifBlank { url }, url, folder = folder, iconPath = iconPath)
        bookmarks = bookmarks + item
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

    fun exportHtml(): String {
        val rows = bookmarks.joinToString("\n") {
            """        <DT><A HREF="${it.url.replace("\"", "&quot;")}" ADD_DATE="${it.createdAt / 1000}">${it.title.replace("<", "&lt;").replace("&", "&amp;")}</A>"""
        }
        return """<!DOCTYPE NETSCAPE-Bookmark-file-1>
<META HTTP-EQUIV="Content-Type" CONTENT="text/html; charset=UTF-8">
<TITLE>Bookmarks</TITLE>
<H1>Bookmarks</H1>
<DL><p>
$rows
</DL><p>
"""
    }

    private fun persist() {
        prefs.edit().putString("bookmarks", Bookmark.listToJson(bookmarks)).apply()
    }
}

class HistoryRepository(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val idGen = AtomicLong(System.currentTimeMillis())
    var entries by mutableStateOf(HistoryEntry.listFromJson(prefs.getString("history", "[]")))
        private set

    fun record(title: String, url: String, incognito: Boolean = false) {
        if (incognito || !url.startsWith("http")) return
        val now = System.currentTimeMillis()
        val latest = entries.firstOrNull()
        entries = if (latest != null && latest.url == url && now - latest.visitedAt < 120_000) {
            listOf(latest.copy(title = title.ifBlank { latest.title }, visitedAt = now)) + entries.drop(1)
        } else {
            (listOf(HistoryEntry(idGen.incrementAndGet(), title.ifBlank { url }, url, now)) + entries).take(3000)
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

    private fun persist() {
        prefs.edit().putString("history", HistoryEntry.listToJson(entries)).apply()
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
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, safeName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
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
    val settings = SettingsRepository(context)
    val bookmarks = BookmarkRepository(context, scope)
    val history = HistoryRepository(context, scope)
    val downloads = DownloadRepository(context, scope)
    val tabs = TabStore(context)
    val filesDir: File = context.filesDir
}
