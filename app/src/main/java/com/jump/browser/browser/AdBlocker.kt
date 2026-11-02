package com.jump.browser.browser

/**
 * A deliberately small, transparent built-in blocklist. It is not a copy of a
 * commercial filter list: the goal is to demonstrate the interception pipeline
 * (network hosts + cosmetic rules) in a way that can be extended by users.
 */
object AdBlocker {
    private val hosts = setOf(
        "doubleclick.net",
        "googleadservices.com",
        "googlesyndication.com",
        "googletagservices.com",
        "googletagmanager.com",
        "google-analytics.com",
        "analytics.google.com",
        "adservice.google.com",
        "pagead2.googlesyndication.com",
        "partner.googleadservices.com",
        "adnxs.com",
        "appnexus.com",
        "rubiconproject.com",
        "pubmatic.com",
        "openx.net",
        "criteo.com",
        "criteo.net",
        "taboola.com",
        "outbrain.com",
        "moatads.com",
        "advertising.com",
        "adform.net",
        "adsrvr.org",
        "casalemedia.com",
        "contextweb.com",
        "indexexchange.com",
        "sharethrough.com",
        "smartadserver.com",
        "teads.tv",
        "yieldmo.com",
        "zemanta.com",
        "amazon-adsystem.com",
        "media.net",
        "serving-sys.com",
        "sizmek.com",
        "zedo.com",
        "adcolony.com",
        "applovin.com",
        "chartboost.com",
        "unityads.unity3d.com",
        "vungle.com",
        "ironsrc.com",
        "facebook.net",
        "connect.facebook.net",
        "scorecardresearch.com",
        "quantserve.com",
        "hotjar.com",
        "fullstory.com",
        "mixpanel.com",
        "segment.io",
        "heapanalytics.com",
        "clarity.ms",
        "bugsnag.com",
        "sentry-cdn.com",
        "newrelic.com",
        "optimizely.com",
        "crazyegg.com",
        "yandex.ru/metrika",
        "mc.yandex.ru",
        "ads.yahoo.com",
        "adserver.yahoo.com",
        "flashtalking.com",
        "innovid.com",
        "spotxchange.com",
        "tremorhub.com",
        "undertone.com",
        "vidible.tv",
        "adsafeprotected.com",
        "doubleverify.com",
        "iasds01.com",
        "moatpixel.com",
    )

    /** Sites the user explicitly excluded with "Mark as ad" -> "Not an ad". */
    private val exceptions = LinkedHashSet<String>()

    fun addSiteException(host: String?) {
        if (host.isNullOrBlank()) return
        synchronized(exceptions) { exceptions.add(host.lowercase()) }
    }

    fun isSiteException(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        return synchronized(exceptions) { host.lowercase() in exceptions }
    }

    /**
     * Request interception calls this for every subresource, so it must not
     * scan the whole list. Checking each suffix of the host (at most a handful)
     * against a set turns it into a constant number of hash lookups instead of
     * dozens of `endsWith` calls per request.
     */
    fun isBlocked(url: String?): Boolean {
        if (url == null) return false
        val host = UrlUtils.host(url).lowercase()
        if (host.isEmpty() || isSiteException(host)) return false
        if (host in hosts) return true
        var index = host.indexOf('.')
        while (index != -1) {
            if (host.substring(index + 1) in hosts) return true
            index = host.indexOf('.', index + 1)
        }
        return false
    }

    val cosmeticCss: String = """
        [id^="google_ads"], [id^="div-gpt-ad"], [class^="ad-slot"],
        [class*=" adsbygoogle"], ins.adsbygoogle, .advertisement,
        [data-ad-slot], [data-ad-client] { display: none !important; }
    """.trimIndent()
}
