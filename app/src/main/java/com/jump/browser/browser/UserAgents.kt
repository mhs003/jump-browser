package com.jump.browser.browser

import com.jump.browser.data.AppSettings
import com.jump.browser.data.ClearDataFlag

/**
 * User-agent choices, mirroring Via's list and its reduction rule.
 *
 * Via keeps the browsing mode and the agent separate: "Desktop site" is a
 * per-tab toggle, while Settings → User-agent picks the string that is sent.
 * [desktopAgent] is used when the tab is in desktop mode; [mobileAgent] is used
 * otherwise. Both honour [AppSettings.userAgentReduction].
 */
object UserAgents {

    /** Settings value to label, as shown in the summary row. */
    fun label(settings: AppSettings): String? = when (settings.userAgent) {
        "DEFAULT" -> null
        "ANDROID" -> "Android (Phone)"
        "ANDROID_TABLET" -> "Android (Tablet)"
        "WINDOWS_CHROME" -> "Chrome (PC)"
        "WINDOWS_IE" -> "Windows (IE 11)"
        "IPHONE" -> "iPhone"
        "IPAD" -> "iPad"
        "SYMBIAN" -> "Symbian"
        "CUSTOM" -> settings.customUserAgent.ifBlank { null }
        else -> null
    }

    /** The user agent sent for a normal (non-desktop) tab. */
    fun mobileAgent(settings: AppSettings, fallback: String): String {
        val chosen = when (settings.userAgent) {
            "DEFAULT" -> fallback
            "CUSTOM" -> settings.customUserAgent.ifBlank { fallback }
            else -> settings.userAgent.ifBlank { fallback }
        }
        return reduce(chosen, settings.userAgentReduction)
    }

    /** The user agent sent for a desktop-mode tab. */
    fun desktopAgent(settings: AppSettings, fallback: String): String {
        val chosen = when {
            settings.desktopUserAgent.isNotBlank() -> settings.desktopUserAgent
            settings.userAgent == "CUSTOM" && settings.customUserAgent.isNotBlank() -> settings.customUserAgent
            else -> DESKTOP
        }
        return reduce(chosen, settings.userAgentReduction)
    }

    const val DESKTOP: String =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/126.0.0.0 Safari/537.36"

    /**
     * Drops the Android build and minor version details, which are the parts
     * that make a browser fingerprint distinctive.
     */
    fun reduce(agent: String, enabled: Boolean): String {
        if (!enabled) return agent
        var out = agent
        // Android 14; Pixel 8 Build/UQ1A.240205.002 → Android 14
        out = out.replace(Regex("""(Android \d+(?:\.\d+)?)[^;)]*;?[^;)]*?Build/[^;)]*"""), "$1")
        // Chrome/126.0.0.0 → Chrome/126.0.0.0 is already coarse; trim minor.
        out = out.replace(Regex("""Chrome/(\d+)\.\d+\.\d+\.\d+"""), "Chrome/$1.0.0.0")
        out = out.replace(Regex("""Version/(\d+)\.\d+(?:\.\d+)?"""), "Version/$1.0")
        out = out.replace(Regex("""\s{2,}"""), " ")
        return out.trim()
    }
}

/** The Clear data dialogs' option order, matching Via's check list. */
val CLEAR_DATA_OPTIONS: List<Pair<String, Int>> = listOf(
    "Cache" to ClearDataFlag.CACHE,
    "Form data" to ClearDataFlag.FORM_DATA,
    "History" to ClearDataFlag.HISTORY,
    "Closed tabs" to ClearDataFlag.CLOSED_TABS,
    "Web storage" to ClearDataFlag.WEB_STORAGE,
    "Cookies (Login status)" to ClearDataFlag.COOKIES,
    "App cache" to ClearDataFlag.APP_CACHE,
)
