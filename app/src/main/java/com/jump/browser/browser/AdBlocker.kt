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

    fun isBlocked(url: String?): Boolean {
        if (url == null) return false
        val host = UrlUtils.host(url).lowercase()
        if (host.isEmpty()) return false
        return hosts.any { blocked -> host == blocked || host.endsWith(".$blocked") }
    }

    val cosmeticCss: String = """
        [id^="google_ads"], [id^="div-gpt-ad"], [class^="ad-slot"],
        [class*=" adsbygoogle"], ins.adsbygoogle, .advertisement,
        [data-ad-slot], [data-ad-client] { display: none !important; }
    """.trimIndent()
}
