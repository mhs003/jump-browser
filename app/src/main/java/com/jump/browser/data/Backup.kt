package com.jump.browser.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * A full backup of the user's data as one JSON document.
 *
 * This is what "Export data" writes and "Import data" reads. It deliberately
 * excludes anything that cannot travel safely:
 * - saved passwords never leave the device, not even in a backup file, because
 *   the file is plain JSON the user may move anywhere;
 * - browsing history travels, since it is the main thing people want to carry
 *   between installs.
 */
object Backup {
    const val FORMAT = "jump-backup"
    const val VERSION = 1

    /** Everything that can be exported. */
    data class Snapshot(
        val settings: JSONObject,
        val bookmarks: JSONArray,
        val history: JSONArray,
    )

    fun build(
        settings: JSONObject,
        bookmarks: List<Bookmark>,
        history: List<HistoryEntry>,
    ): String = JSONObject()
        .put("format", FORMAT)
        .put("version", VERSION)
        .put("exportedAt", System.currentTimeMillis())
        .put("settings", settings)
        .put("bookmarks", JSONArray(Bookmark.listToJson(bookmarks)))
        .put("history", JSONArray(HistoryEntry.listToJson(history)))
        .toString(2)

    sealed interface Result {
        data class Ok(val snapshot: Snapshot) : Result

        /** The file was not a backup this version understands. */
        data class Invalid(val reason: String) : Result
    }

    fun parse(raw: String?): Result {
        if (raw.isNullOrBlank()) return Result.Invalid("The file is empty")
        val root = runCatching { JSONObject(raw) }.getOrNull()
            ?: return Result.Invalid("The file is not valid JSON")
        if (root.optString("format") != FORMAT) {
            return Result.Invalid("This is not a Jump backup file")
        }
        val version = root.optInt("version", 0)
        if (version > VERSION) {
            return Result.Invalid("This backup was made by a newer version of Jump")
        }
        return Result.Ok(
            Snapshot(
                settings = root.optJSONObject("settings") ?: JSONObject(),
                bookmarks = root.optJSONArray("bookmarks") ?: JSONArray(),
                history = root.optJSONArray("history") ?: JSONArray(),
            ),
        )
    }

    /** Settings that must never be restored from a file (device-specific). */
    val deviceOnlySettings = setOf("homepageLogoPath", "homepageBackgroundPath")
}
