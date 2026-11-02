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
    val fullscreen: Boolean = false,
    val customHomeCss: String = "",
    val homepageLogoPath: String = "",
    val homepageBackgroundPath: String = "",
    val homepageBackgroundOpacity: Int = 100,
    val homepageSearchBar: Boolean = true,
    val homepageFavoriteColor: Boolean = true,
    val toolbarPosition: String = "TOP",        // TOP, BOTTOM, BOTTOM_SLIM
    val toolbarLayout: String = "ADDRESS_FIRST", // ADDRESS_FIRST, TITLE_AND_ADDRESS
    val toolbarSearch: Boolean = true,
    val customizeMenu: List<String> = emptyList(),
    val userAgent: String = "",
) {
    val searchEngine: SearchEngine
        get() {
            val engine = SearchEngine.byId(searchEngineId)
            return if (engine.id == "custom" && customSearchUrl.contains("%s")) {
                engine.copy(template = customSearchUrl)
            } else engine
        }
}
