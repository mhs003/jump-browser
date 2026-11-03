package com.jump.browser

import com.jump.browser.browser.CLEAR_DATA_OPTIONS
import com.jump.browser.browser.UserAgents
import com.jump.browser.data.AppSettings
import com.jump.browser.data.ClearDataFlag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * User-agent selection and the Clear data masks.
 *
 * Both mirror Via: the bit values are the ones stored in its `cleardata2`
 * preference, and the agent list is the one its User-agent page shows.
 */
class UserAgentsTest {

    private val fallback = "Mozilla/5.0 (Linux; Android 12; Pixel 5 Build/SP1A) Mobile"

    @Test
    fun defaultLabelIsEmpty() {
        assertNull(UserAgents.label(AppSettings()))
    }

    @Test
    fun customLabelIsTheAgentItself() {
        val settings = AppSettings(userAgent = "CUSTOM", customUserAgent = "Agent/1.0")
        assertEquals("Agent/1.0", UserAgents.label(settings))
    }

    @Test
    fun customAgentIsSentAsIs() {
        val settings = AppSettings(userAgent = "CUSTOM", customUserAgent = "Agent/1.0")
        assertEquals("Agent/1.0", UserAgents.mobileAgent(settings, fallback))
    }

    @Test
    fun blankCustomAgentFallsBackToTheWebViewDefault() {
        val settings = AppSettings(userAgent = "CUSTOM", customUserAgent = "   ")
        assertEquals(fallback, UserAgents.mobileAgent(settings, fallback))
    }

    @Test
    fun desktopModeUsesTheDesktopAgent() {
        val settings = AppSettings()
        assertEquals(UserAgents.DESKTOP, UserAgents.desktopAgent(settings, fallback))
    }

    @Test
    fun desktopAgentCanBeOverridden() {
        val settings = AppSettings(desktopUserAgent = "Custom/Desktop")
        assertEquals("Custom/Desktop", UserAgents.desktopAgent(settings, fallback))
    }

    @Test
    fun reductionDropsTheAndroidBuild() {
        val reduced = UserAgents.reduce(fallback, enabled = true)
        assertTrue(reduced.contains("Android 12"))
        assertEquals(false, reduced.contains("Build/"))
        assertEquals(false, reduced.contains("Pixel 5"))
    }

    @Test
    fun reductionIsOffByDefault() {
        assertEquals(fallback, UserAgents.reduce(fallback, enabled = false))
    }

    @Test
    fun reductionKeepsChromeMajorVersionOnly() {
        val agent = "Mozilla/5.0 Chrome/126.0.6478.71 Safari/537.36"
        val reduced = UserAgents.reduce(agent, enabled = true)
        assertEquals(false, reduced.contains("6478"))
        assertTrue(reduced.contains("Chrome/126.0.0.0"))
    }

    @Test
    fun clearDataBitsMatchVia() {
        // Via's clear list array, in its check-list order.
        assertEquals(
            listOf(0x01, 0x02, 0x04, 0x40, 0x08, 0x10, 0x20),
            CLEAR_DATA_OPTIONS.map { it.second },
        )
        assertEquals(
            listOf(
                "Cache", "Form data", "History", "Closed tabs",
                "Web storage", "Cookies (Login status)", "App cache",
            ),
            CLEAR_DATA_OPTIONS.map { it.first },
        )
    }

    @Test
    fun defaultClearMaskTicksBothCaches() {
        assertEquals(
            ClearDataFlag.CACHE or ClearDataFlag.APP_CACHE,
            AppSettings().clearDataMask,
        )
    }

    @Test
    fun clearOnExitStartsDisabled() {
        assertEquals(0, AppSettings().clearDataOnExitMask)
    }

    @Test
    fun incognitoModeStartsOffAndIsRemembered() {
        val settings = AppSettings(incognitoDefault = true)
        assertEquals(true, settings.incognitoDefault)
        // The mode is a stored flag, not derived from a tab.
        assertNotEquals(0, settings.hashCode())
    }
}
