package com.jump.browser

import com.jump.browser.data.UserScript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Userscript metadata parsing and `@match` pattern matching. The patterns are
 * globs, which is easy to get subtly wrong, so each shape is pinned here.
 */
class UserScriptTest {

    private val source = """
        // ==UserScript==
        // @name  Page Tweaker
        // @match *://*.example.com/*
        // @exclude *://admin.example.com/*
        // @run-at document-end
        // ==/UserScript==
        document.body.style.background = "black";
    """.trimIndent()

    @Test
    fun readsMetadataBlock() {
        val script = UserScript.parse(source, 1)
        assertEquals("Page Tweaker", script.name)
        assertEquals(listOf("*://*.example.com/*"), script.matches)
        assertEquals(listOf("*://admin.example.com/*"), script.excluded)
        assertTrue(script.runAtStart)
    }

    @Test
    fun readsSingleLineMetadataBlock() {
        // A block pasted as one line (as some sites and chat clients produce it)
        // must still yield separate directives.
        val single = "// ==UserScript== // @name One Line // @match *://example.com/* " +
            "// ==/UserScript== document.title='x';"
        val script = UserScript.parse(single, 3)
        assertEquals("One Line", script.name)
        assertEquals(listOf("*://example.com/*"), script.matches)
        assertTrue(script.appliesTo("https://example.com/a"))
    }

    @Test
    fun nameFallsBackToFirstCodeLine() {
        val script = UserScript.parse("console.log('hi');", 2)
        assertEquals("console.log('hi');", script.name)
    }

    @Test
    fun matchesSubdomainsAndPaths() {
        val script = UserScript.parse(source, 1)
        assertTrue(script.appliesTo("https://example.com/"))
        assertTrue(script.appliesTo("https://www.example.com/page?q=1"))
        // Excluded pattern wins.
        assertFalse(script.appliesTo("https://admin.example.com/"))
        assertFalse(script.appliesTo("https://other.com/"))
    }

    @Test
    fun noPatternsAppliesEverywhere() {
        val script = UserScript(id = 1, name = "all", source = "")
        assertTrue(script.appliesTo("https://anything.test/"))
        // Only real pages; the homepage and about:blank are not http.
        assertFalse(script.appliesTo("about:blank"))
        assertFalse(script.appliesTo("file:///sdcard/x.html"))
    }

    @Test
    fun disabledScriptsNeverApply() {
        val script = UserScript(id = 1, name = "off", source = "", enabled = false)
        assertFalse(script.appliesTo("https://example.com/"))
    }

    @Test
    fun dottedPatternIsLiteral() {
        val script = UserScript(id = 1, name = "dot", source = "", matches = listOf("https://a.b.c/*"))
        assertTrue(script.appliesTo("https://a.b.c/x"))
        // The dots must not act as wildcards.
        assertFalse(script.appliesTo("https://axbx c/x"))
    }
}
