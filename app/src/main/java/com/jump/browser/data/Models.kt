package com.jump.browser.data

import org.json.JSONArray
import org.json.JSONObject

data class Bookmark(
    val id: Long,
    val title: String,
    val url: String,
    val createdAt: Long = System.currentTimeMillis(),
    val folder: String = "",
    val iconPath: String = "",
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("url", url)
        .put("createdAt", createdAt)
        .put("folder", folder)
        .put("iconPath", iconPath)

    companion object {
        fun fromJson(o: JSONObject) = Bookmark(
            id = o.optLong("id"),
            title = o.optString("title"),
            url = o.optString("url"),
            createdAt = o.optLong("createdAt"),
            folder = o.optString("folder"),
            iconPath = o.optString("iconPath"),
        )

        fun listToJson(items: List<Bookmark>): String =
            JSONArray().also { arr -> items.forEach { arr.put(it.toJson()) } }.toString()

        fun listFromJson(raw: String?): List<Bookmark> = runCatching {
            val arr = JSONArray(raw ?: "[]")
            (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }
}

data class HistoryEntry(
    val id: Long,
    val title: String,
    val url: String,
    val visitedAt: Long,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("url", url)
        .put("visitedAt", visitedAt)

    companion object {
        fun fromJson(o: JSONObject) = HistoryEntry(
            id = o.optLong("id"),
            title = o.optString("title"),
            url = o.optString("url"),
            visitedAt = o.optLong("visitedAt"),
        )

        fun listToJson(items: List<HistoryEntry>): String =
            JSONArray().also { arr -> items.forEach { arr.put(it.toJson()) } }.toString()

        fun listFromJson(raw: String?): List<HistoryEntry> = runCatching {
            val arr = JSONArray(raw ?: "[]")
            (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }
}

data class TabSnapshot(
    val url: String,
    val title: String = "",
    val incognito: Boolean = false,
    val desktopMode: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("url", url)
        .put("title", title)
        .put("incognito", incognito)
        .put("desktopMode", desktopMode)

    companion object {
        fun listToJson(items: List<TabSnapshot>): String =
            JSONArray().also { arr -> items.forEach { arr.put(it.toJson()) } }.toString()

        fun listFromJson(raw: String?): List<TabSnapshot> = runCatching {
            val arr = JSONArray(raw ?: "[]")
            (0 until arr.length()).map { o ->
                val j = arr.getJSONObject(o)
                TabSnapshot(
                    url = j.optString("url"),
                    title = j.optString("title"),
                    incognito = j.optBoolean("incognito"),
                    desktopMode = j.optBoolean("desktopMode"),
                )
            }
        }.getOrDefault(emptyList())
    }
}

enum class DarkMode { SYSTEM, LIGHT, DARK }

data class SearchEngine(
    val id: String,
    val name: String,
    val template: String,
    val suggestUrl: String = "",
) {
    fun searchUrl(query: String) = template.replace("%s", java.net.URLEncoder.encode(query, "UTF-8"))

    companion object {
        val GOOGLE = SearchEngine(
            "google", "Google", "https://www.google.com/search?q=%s",
            "https://suggestqueries.google.com/complete/search?client=firefox&q=%s",
        )
        val DUCKDUCKGO = SearchEngine(
            "duckduckgo", "DuckDuckGo", "https://duckduckgo.com/?q=%s",
            "https://duckduckgo.com/ac/?q=%s&type=list",
        )
        val BING = SearchEngine(
            "bing", "Bing", "https://www.bing.com/search?q=%s",
            "https://api.bing.com/osjson.aspx?query=%s",
        )
        val BAIDU = SearchEngine("baidu", "Baidu", "https://www.baidu.com/s?wd=%s")
        val YAHOO = SearchEngine("yahoo", "Yahoo", "https://search.yahoo.com/search?p=%s")
        val STARTPAGE = SearchEngine("startpage", "StartPage", "https://www.startpage.com/do/search?query=%s")
        val BRAVE = SearchEngine("brave", "Brave", "https://search.brave.com/search?q=%s")
        val YANDEX = SearchEngine("yandex", "Yandex", "https://yandex.com/search/?text=%s")
        val CUSTOM = SearchEngine("custom", "Custom", "https://www.google.com/search?q=%s")

        /** The reference dialog lists exactly these engines, in this order. */
        val reference = listOf(GOOGLE, BAIDU, BING, YAHOO, STARTPAGE, DUCKDUCKGO)

        val all = listOf(GOOGLE, BAIDU, BING, YAHOO, STARTPAGE, DUCKDUCKGO, BRAVE, YANDEX, CUSTOM)

        fun byId(id: String): SearchEngine = all.firstOrNull { it.id == id } ?: GOOGLE
    }
}

/**
 * What the Clear data action wipes, using the same bits as Via's
 * `cleardata2` preference so the dialogs and masks line up one to one.
 */
object ClearDataFlag {
    const val CACHE = 0x01
    const val FORM_DATA = 0x02
    const val HISTORY = 0x04
    const val CLOSED_TABS = 0x40
    const val WEB_STORAGE = 0x08
    const val COOKIES = 0x10
    const val APP_CACHE = 0x20
}

/** Cache and App cache are pre-selected, as in Via's confirmation dialog. */
const val DEFAULT_CLEAR_MASK: Int = ClearDataFlag.CACHE or ClearDataFlag.APP_CACHE

data class AppSettings(
    val searchEngineId: String = "google",
    val customSearchUrl: String = "",
    val homepageUrl: String = "",
    val darkMode: DarkMode = DarkMode.LIGHT,
    val adBlock: Boolean = true,
    val blockThirdPartyCookies: Boolean = false,
    val javaScript: Boolean = true,
    val loadImages: Boolean = true,
    val doNotTrack: Boolean = false,
    val desktopMode: Boolean = false,
    val restoreTabs: Boolean = true,
    val searchSuggestions: Boolean = true,
    val readerMode: Boolean = false,
    val textZoom: Int = 100,
    val incognitoDefault: Boolean = false,
    val confirmExit: Boolean = true,
    val nightMode: Boolean = false,
    /** Save logins locally and offer to fill them. */
    val savePasswords: Boolean = false,
    /** Assistant endpoint settings; empty means not configured. */
    val ai: ChatSettings = ChatSettings(),
    /** "system", "portrait", "landscape" or "sensor". */
    val orientation: String = "system",
    val fullscreen: Boolean = false,
    val customHomeCss: String = "",
    val homepageLogoPath: String = "",
    val homepageBackgroundPath: String = "",
    val homepageBackgroundOpacity: Int = 100,
    val homepageSearchBar: Boolean = true,
    val homepageFavoriteColor: Boolean = true,
    /** Toolbar UI mode: SANDWICH, TOP, BOTTOM or TWO_ROW. */
    val toolbarPosition: String = "TOP",
    /** What the address bar shows: TITLE, URL or DOMAIN. */
    val urlFieldContent: String = "TITLE",
    /** Show the tab bar (two-row mode only). */
    val showTabBar: Boolean = true,
    /** NEVER, SCROLL or PRESS. */
    val autoHideToolbars: String = "NEVER",
    /** Show a search field in the toolbar. */
    val searchToolbar: Boolean = false,
    /** AUTO, DARK or LIGHT. */
    val toolbarColor: String = "AUTO",
    val autoAdjustToolbarColor: Boolean = true,
    val customizeMenu: List<String> = emptyList(),
    /**
     * Base user agent: DEFAULT, DESKTOP, ANDROID, ANDROID_TABLET,
     * WINDOWS_CHROME, WINDOWS_IE, IPHONE or IPAD. [customUserAgent] is used
     * when this is CUSTOM.
     */
    val userAgent: String = "DEFAULT",
    val customUserAgent: String = "",
    /** The desktop-mode user agent actually sent when desktop mode is on. */
    val desktopUserAgent: String = "",
    /** Strip device and minor-version details from the user agent. */
    val userAgentReduction: Boolean = false,
    /** Bit mask of what the Clear data dialog wipes. */
    val clearDataMask: Int = DEFAULT_CLEAR_MASK,
    /** Bit mask of what is wiped when the browser exits. Empty means never. */
    val clearDataOnExitMask: Int = 0,
    /** Show the "tab closed" toast, which offers an undo. */
    val showUndoToast: Boolean = true,
) {
    val searchEngine: SearchEngine
        get() {
            val engine = SearchEngine.byId(searchEngineId)
            return if (engine.id == "custom" && customSearchUrl.contains("%s")) {
                engine.copy(template = customSearchUrl)
            } else engine
        }
}
