package com.jump.browser.ui

import com.jump.browser.data.Bookmark
import com.jump.browser.data.HistoryEntry
import com.jump.browser.data.SearchEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONTokener
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

enum class SuggestionKind { CURRENT, HISTORY, BOOKMARK, SEARCH }

data class SearchSuggestion(
    val text: String,
    val url: String? = null,
    val kind: SuggestionKind = SuggestionKind.SEARCH,
    val subtitle: String? = null,
)

object SuggestionSource {
    suspend fun load(
        engine: SearchEngine,
        query: String,
        history: List<HistoryEntry>,
        bookmarks: List<Bookmark>,
        enabled: Boolean,
    ): List<SearchSuggestion> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val q = query.lowercase()
        val local = mutableListOf<SearchSuggestion>()
        bookmarks.asSequence()
            .filter { it.title.lowercase().contains(q) || it.url.lowercase().contains(q) }
            .take(3)
            .forEach {
                local += SearchSuggestion(it.title, it.url, SuggestionKind.BOOKMARK, it.url)
            }
        history.asSequence()
            .filter { it.title.lowercase().contains(q) || it.url.lowercase().contains(q) }
            .take(4)
            .forEach {
                local += SearchSuggestion(it.title, it.url, SuggestionKind.HISTORY, it.url)
            }
        val remote = if (enabled && engine.suggestUrl.isNotBlank()) {
            fetch(engine.suggestUrl.replace("%s", URLEncoder.encode(query, "UTF-8")))
        } else emptyList()
        val seen = mutableSetOf<String>()
        (remote + local.map { it.text }).asSequence()
            .filter { it.isNotBlank() }
            .filter { seen.add(it.lowercase()) }
            .take(8)
            .map { text ->
                local.firstOrNull { it.text == text }
                    ?: SearchSuggestion(text, engine.searchUrl(text), SuggestionKind.SEARCH)
            }
            .toList()
    }

    private fun fetch(url: String): List<String> {
        return runCatching {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 1800
                readTimeout = 1800
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) Jump/1.0")
            }
            try {
                if (connection.responseCode !in 200..299) return emptyList()
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val parsed = JSONTokener(body).nextValue()
                when (parsed) {
                    is JSONArray -> {
                        if (parsed.length() > 1) {
                            when (val second = parsed.opt(1)) {
                                is JSONArray -> (0 until second.length()).mapNotNull { second.optString(it).takeIf(String::isNotBlank) }
                                else -> emptyList()
                            }
                        } else if (parsed.length() == 1 && parsed.opt(0) is JSONArray) {
                            val first = parsed.optJSONArray(0)
                            (0 until first.length()).mapNotNull { first.optString(it).takeIf(String::isNotBlank) }
                        } else emptyList()
                    }
                    is org.json.JSONObject -> {
                        val arr = parsed.optJSONArray("suggestions") ?: parsed.optJSONArray("results")
                        if (arr == null) emptyList() else (0 until arr.length()).mapNotNull { i ->
                            val o = arr.optJSONObject(i)
                            o?.optString("phrase")?.takeIf(String::isNotBlank)
                                ?: arr.optString(i).takeIf(String::isNotBlank)
                        }
                    }
                    else -> emptyList()
                }
            } finally {
                connection.disconnect()
            }
        }.getOrDefault(emptyList())
    }
}
