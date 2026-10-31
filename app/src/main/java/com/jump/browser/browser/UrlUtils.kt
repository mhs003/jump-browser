package com.jump.browser.browser

import com.jump.browser.data.SearchEngine
import java.net.URI
import java.net.URLEncoder
import java.util.Locale

object UrlUtils {
    private val ipv4 = Regex("""^\d{1,3}(\.\d{1,3}){3}(:\d+)?(/.*)?$""")
    private val domain = Regex("""^[\p{L}0-9]([\p{L}0-9-]*[\p{L}0-9])?(\.[\p{L}0-9]([\p{L}0-9-]*[\p{L}0-9])?)+(:\d+)?(/.*)?$""")
    private val scheme = Regex("""^[a-zA-Z][a-zA-Z0-9+.-]*:.*$""")

    fun toUrlOrSearch(input: String, engine: SearchEngine): String? {
        val text = input.trim()
        if (text.isEmpty()) return null
        if (text.startsWith("http://", true) || text.startsWith("https://", true) ||
            text.startsWith("file://", true) || text.startsWith("about:", true) ||
            text.startsWith("data:", true)
        ) return text
        if (text.startsWith("javascript:", true)) return null
        if (text.contains(" ")) return engine.searchUrl(text)
        val withoutScheme = text.removePrefix("//")
        if (ipv4.matches(withoutScheme) || withoutScheme.startsWith("localhost") ||
            domain.matches(withoutScheme)
        ) {
            val host = withoutScheme.substringBefore('/')
            val secure = !(host.startsWith("localhost") || ipv4.matches(host))
            return (if (secure) "https://" else "http://") + withoutScheme
        }
        if (scheme.matches(text)) return text
        return engine.searchUrl(text)
    }

    fun isSearchQuery(input: String): Boolean {
        val t = input.trim()
        return t.contains(" ") || (!t.contains(".") && !scheme.matches(t))
    }

    fun host(url: String?): String = runCatching {
        if (url.isNullOrBlank()) return ""
        if (!url.contains("://")) return url.substringBefore('/')
        URI(url).host ?: ""
    }.getOrDefault("")

    fun displayHost(url: String?): String {
        val h = host(url)
        return h.removePrefix("www.")
    }

    fun prettyUrl(url: String?): String {
        if (url.isNullOrBlank() || url == "about:blank") return ""
        return url
    }

    fun isHttp(url: String?): Boolean =
        url != null && (url.startsWith("http://", true) || url.startsWith("https://", true))

    fun isSecure(url: String?): Boolean = url != null && url.startsWith("https://", true)

    fun encodeQuery(query: String): String = URLEncoder.encode(query, "UTF-8")

    fun titleFor(url: String): String {
        val h = displayHost(url)
        return h.ifBlank { url }
    }

    fun stripFragment(url: String): String = url.substringBefore('#')

    fun sameSite(a: String?, b: String?): Boolean {
        if (a == null || b == null) return false
        val ha = host(a).lowercase(Locale.ROOT)
        val hb = host(b).lowercase(Locale.ROOT)
        if (ha.isEmpty() || hb.isEmpty()) return false
        fun base(h: String): String {
            val parts = h.split('.')
            return if (parts.size >= 2) parts.takeLast(2).joinToString(".") else h
        }
        return base(ha) == base(hb)
    }

    fun ensureHttp(url: String): String = when {
        url.startsWith("http://") || url.startsWith("https://") -> url
        url.startsWith("//") -> "https:$url"
        else -> "https://$url"
    }
}
