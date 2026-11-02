package com.jump.browser.data

import org.json.JSONArray
import org.json.JSONObject

/** A user script: JavaScript plus the metadata block browsers understand. */
data class UserScript(
    val id: Long,
    val name: String,
    val source: String,
    val matches: List<String> = emptyList(),
    val excluded: List<String> = emptyList(),
    val runAtStart: Boolean = true,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
) {
    /**
     * Whether this script should run for [url].
     *
     * Supports the `@match` patterns userscripts use: `*` for any run of
     * characters and `*.example.com`-style subdomain wildcards. A script with
     * no patterns applies to every page, which is how the reference treats
     * scripts without a metadata block.
     */
    fun appliesTo(url: String): Boolean {
        if (!enabled) return false
        if (!url.startsWith("http")) return false
        if (matches.isEmpty()) return excluded.none { glob(it, url) }
        if (excluded.any { glob(it, url) }) return false
        return matches.any { glob(it, url) }
    }

    private fun glob(pattern: String, url: String): Boolean {
        if (pattern.isBlank()) return false
        val regex = buildString {
            append('^')
            var i = 0
            while (i < pattern.length) {
                // The scheme separator is literal: treating it as part of a `*`
                // lets the wildcard swallow `://` and stops patterns like
                // `*://*.example.com/*` from ever matching.
                if (pattern.startsWith("://", i)) {
                    append("://")
                    i += 3
                    continue
                }
                val ch = pattern[i]
                when {
                    // `*.example.com` covers the bare domain and any subdomain.
                    ch == '*' && pattern.startsWith("*.", i) -> {
                        append("(?:[^./]+\\.)*")
                        i += 2
                        continue
                    }
                    ch == '*' -> append(".*")
                    ch.isLetterOrDigit() || ch == ':' || ch == '/' || ch == '-' ||
                        ch == '_' || ch == '~' || ch == '%' || ch == '&' || ch == '=' ->
                        append(ch)
                    else -> append('\\').append(ch)
                }
                i++
            }
            append('$')
        }
        return Regex(regex, RegexOption.IGNORE_CASE).matches(url)
    }

    companion object {
        private val metaBlock = Regex("""==UserScript==(.*?)==/UserScript==""", RegexOption.DOT_MATCHES_ALL)
        private val metaLine = Regex("""@([a-zA-Z:-]+)\s+(.+)""")

        /**
         * Reads the `// ==UserScript==` header, if any. The script body is kept
         * verbatim so it runs exactly as written.
         */
        fun parse(source: String, id: Long): UserScript {
            var name = ""
            val matches = mutableListOf<String>()
            val excluded = mutableListOf<String>()
            var runAt = "document-idle"

            metaBlock.find(source)?.groupValues?.getOrNull(1)?.let { block ->
                block.lineSequence().forEach { raw ->
                    val line = raw.trim().removePrefix("//").removePrefix("*").trim()
                    val m = metaLine.find(line) ?: return@forEach
                    val key = m.groupValues[1].lowercase()
                    val value = m.groupValues[2].trim()
                    when (key) {
                        "name" -> if (name.isBlank()) name = value
                        "match", "include" -> matches += value
                        "exclude" -> excluded += value
                        "run-at" -> runAt = value
                    }
                }
            }

            val trimmed = source.trim()
            if (name.isBlank()) {
                name = trimmed.lineSequence()
                    .firstOrNull { it.isNotBlank() && !it.trimStart().startsWith("//") }
                    ?.take(60)
                    ?.trim()
                    .orEmpty()
                if (name.isBlank()) name = "Untitled script"
            }
            return UserScript(
                id = id,
                name = name,
                source = source,
                matches = matches,
                excluded = excluded,
                runAtStart = runAt.contains("document-start") || runAt.contains("document-end"),
            )
        }
    }
}

/** Persistence helpers shared by the repository and tests. */
internal object UserScriptJson {
    fun listToJson(items: List<UserScript>): String =
        JSONArray().also { arr -> items.forEach { arr.put(toJson(it)) } }.toString()

    fun toJson(s: UserScript): JSONObject = JSONObject()
        .put("id", s.id)
        .put("name", s.name)
        .put("source", s.source)
        .put("matches", JSONArray(s.matches))
        .put("excluded", JSONArray(s.excluded))
        .put("runAtStart", s.runAtStart)
        .put("enabled", s.enabled)
        .put("createdAt", s.createdAt)

    fun listFromJson(raw: String?): List<UserScript> = runCatching {
        val arr = JSONArray(raw ?: "[]")
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            UserScript(
                id = o.optLong("id"),
                name = o.optString("name"),
                source = o.optString("source"),
                matches = o.optJSONArray("matches")?.let { a ->
                    (0 until a.length()).map { a.optString(it) }
                }.orEmpty(),
                excluded = o.optJSONArray("excluded")?.let { a ->
                    (0 until a.length()).map { a.optString(it) }
                }.orEmpty(),
                runAtStart = o.optBoolean("runAtStart", true),
                enabled = o.optBoolean("enabled", true),
                createdAt = o.optLong("createdAt"),
            )
        }
    }.getOrDefault(emptyList())
}
