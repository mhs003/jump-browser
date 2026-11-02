package com.jump.browser

import com.jump.browser.data.Backup
import com.jump.browser.data.Bookmark
import com.jump.browser.data.HistoryEntry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupTest {

    @Test
    fun roundTripsAndOmitsPasswords() {
        val json = Backup.build(
            settings = JSONObject().put("nightMode", true).put("darkMode", "DARK"),
            bookmarks = listOf(Bookmark(1, "A", "https://a.example/", folder = "Reading")),
            history = listOf(HistoryEntry(1, "A", "https://a.example/", 1000)),
        )
        val parsed = Backup.parse(json)
        assertTrue(parsed is Backup.Result.Ok)
        val snap = (parsed as Backup.Result.Ok).snapshot
        assertEquals(true, snap.settings.optBoolean("nightMode"))
        assertEquals(1, snap.bookmarks.length())
        assertEquals(1, snap.history.length())
        assertEquals("Reading", snap.bookmarks.getJSONObject(0).optString("folder"))
        // Passwords are never part of a backup, even when the key is absent.
        assertTrue(!json.contains("password", ignoreCase = true))
    }

    @Test
    fun rejectsForeignFiles() {
        assertTrue(Backup.parse("").let { it is Backup.Result.Invalid })
        assertTrue(Backup.parse("not json").let { it is Backup.Result.Invalid })
        assertTrue(
            Backup.parse("""{"format":"chrome-backup","version":1}""")
                .let { it is Backup.Result.Invalid },
        )
    }

    @Test
    fun rejectsNewerFormatVersions() {
        val newer = JSONObject()
            .put("format", Backup.FORMAT)
            .put("version", Backup.VERSION + 1)
            .toString()
        val result = Backup.parse(newer)
        assertTrue(result is Backup.Result.Invalid)
    }
}
