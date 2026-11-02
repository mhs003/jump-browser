package com.jump.browser.data

/**
 * Reader for the Netscape bookmark file format — the `bookmarks.html` every
 * desktop browser exports.
 *
 * It is a small streaming walk rather than a full HTML parser: bookmark files
 * are flat and machine-generated, and pulling in an HTML parser for this would
 * cost more than the whole feature. Keeping it free of Android types means the
 * nesting and escaping rules can be unit tested directly.
 */
object NetscapeBookmarks {

    private val tokens = Regex(
        """<H3[^>]*>.*?</H3>|<A\s+[^>]*HREF\s*=\s*["'][^"']+["'][^>]*>.*?</A>|<DL[^>]*>|</DL>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val heading = Regex("""<H3[^>]*>(.*?)</H3>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val link = Regex(
        """<A\s+[^>]*HREF\s*=\s*["']([^"']+)["'][^>]*>(.*?)</A>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val tag = Regex("""<[^>]+>""")

    /** Decodes the handful of entities a bookmark file actually contains. */
    internal fun unescape(value: String): String = value
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&#x27;", "'")
        .replace("&apos;", "'")
        .replace("&amp;", "&")

    /**
     * Parses [html] into bookmarks. [nextId] supplies ids so the caller keeps
     * its own id sequence.
     */
    fun parse(html: String, nextId: () -> Long): List<Bookmark> {
        val out = mutableListOf<Bookmark>()
        // Folder names in document order. A heading applies to the <DL> that
        // follows it, so the folder stack is tracked by that nesting depth.
        val names = mutableListOf<String>()
        var pendingHeading: String? = null
        // Number of <DL> currently open.
        var depth = 0

        for (match in tokens.findAll(html)) {
            val token = match.value
            when {
                token.startsWith("<H3", ignoreCase = true) -> {
                    pendingHeading = heading.find(token)
                        ?.groupValues?.getOrNull(1)
                        ?.let(::unescape)
                        ?.let { tag.replace(it, "") }
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                }

                token.startsWith("</DL", ignoreCase = true) -> {
                    if (depth > 0) depth--
                    // A folder's name belongs to the <DL> it opened; once that
                    // list closes the name no longer applies.
                    while (names.size > depth) names.removeAt(names.lastIndex)
                }

                token.startsWith("<DL", ignoreCase = true) -> {
                    if (pendingHeading != null) {
                        names.add(pendingHeading)
                        pendingHeading = null
                    }
                    depth++
                }

                else -> {
                    val m = link.find(token) ?: continue
                    val url = unescape(m.groupValues[1]).trim()
                    if (!url.startsWith("http") && !url.startsWith("file")) continue
                    val title = unescape(m.groupValues[2])
                        .let { tag.replace(it, "") }
                        .trim()
                        .ifBlank { url }
                    out += Bookmark(
                        id = nextId(),
                        title = title,
                        url = url,
                        folder = names.lastOrNull().orEmpty(),
                    )
                }
            }
        }
        return out
    }
}