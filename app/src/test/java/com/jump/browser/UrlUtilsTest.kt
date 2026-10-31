package com.jump.browser

import com.jump.browser.browser.AdBlocker
import com.jump.browser.browser.UrlUtils
import com.jump.browser.data.SearchEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlUtilsTest {
    private val engine = SearchEngine(
        id = "test",
        name = "Test",
        template = "https://search.example/?q=%s",
    )

    @Test
    fun bareDomainGetsHttps() {
        assertEquals("https://example.com", UrlUtils.toUrlOrSearch("example.com", engine))
    }

    @Test
    fun pathAndQueryArePreserved() {
        assertEquals(
            "https://example.com/a/b?c=d",
            UrlUtils.toUrlOrSearch("example.com/a/b?c=d", engine),
        )
    }

    @Test
    fun explicitlyTypedSchemeIsPreserved() {
        assertEquals("http://example.com", UrlUtils.toUrlOrSearch("http://example.com", engine))
        assertEquals("https://example.com", UrlUtils.toUrlOrSearch("https://example.com", engine))
        assertEquals("file:///sdcard/a.html", UrlUtils.toUrlOrSearch("file:///sdcard/a.html", engine))
    }

    @Test
    fun localhostAndIpUseHttp() {
        assertEquals("http://localhost:8080", UrlUtils.toUrlOrSearch("localhost:8080", engine))
        assertEquals("http://127.0.0.1:8080/x", UrlUtils.toUrlOrSearch("127.0.0.1:8080/x", engine))
    }

    @Test
    fun plainTextBecomesSearch() {
        assertEquals(
            "https://search.example/?q=hello+world",
            UrlUtils.toUrlOrSearch("hello world", engine),
        )
        assertEquals("https://search.example/?q=android", UrlUtils.toUrlOrSearch("android", engine))
    }

    @Test
    fun emptyInputIsIgnored() {
        assertNull(UrlUtils.toUrlOrSearch("   ", engine))
    }

    @Test
    fun hostExtractionIgnoresWww() {
        assertEquals("example.com", UrlUtils.displayHost("https://www.example.com/a"))
        assertTrue(UrlUtils.sameSite("https://www.example.com", "https://api.example.com"))
        assertFalse(UrlUtils.sameSite("https://example.com", "https://example.org"))
    }

    @Test
    fun adHostsBlockKnownDomainsAndSubdomains() {
        assertTrue(AdBlocker.isBlocked("https://ads.doubleclick.net/pixel"))
        assertTrue(AdBlocker.isBlocked("https://www.googletagmanager.com/gtm.js"))
        assertFalse(AdBlocker.isBlocked("https://example.com"))
        assertFalse(AdBlocker.isBlocked("https://notdoubleclick.net"))
    }
}
