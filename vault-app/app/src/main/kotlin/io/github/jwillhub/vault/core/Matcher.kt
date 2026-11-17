package io.github.jwillhub.vault.core

import java.net.URI

/** Decides which saved logins belong to the app or website asking to be filled. */
object Matcher {
    /**
     * A website address reported during autofill is only believed when it comes from one of these
     * browsers. Any other app could claim to be showing "yourbank.com" to be handed that password,
     * so for every other app the login must be linked to the app itself.
     */
    val BROWSERS = setOf(
        "com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary",
        "org.chromium.chrome", "com.google.android.apps.chrome",
        "org.mozilla.firefox", "org.mozilla.firefox_beta", "org.mozilla.fenix", "org.mozilla.focus",
        "org.mozilla.klar", "org.mozilla.fennec_fdroid", "io.github.forkmaintainers.iceraven",
        "com.brave.browser", "com.brave.browser_beta", "com.brave.browser_nightly",
        "com.microsoft.emmx", "com.microsoft.emmx.beta",
        "com.sec.android.app.sbrowser", "com.sec.android.app.sbrowser.beta",
        "com.duckduckgo.mobile.android", "com.opera.browser", "com.opera.browser.beta",
        "com.opera.mini.native", "com.vivaldi.browser", "com.vivaldi.browser.snapshot",
        "com.kiwibrowser.browser", "org.bromite.bromite", "app.vanadium.browser",
        "com.ecosia.android", "org.torproject.torbrowser", "mark.via.gp", "com.mi.globalbrowser",
        "org.cromite.cromite", "us.spotco.fennec_dos", "io.github.ungoogled_chromium.android",
    )

    /** The host of an address as typed by a person: scheme optional, case and a leading "www." ignored. */
    fun hostOf(address: String): String? {
        val trimmed = address.trim()
        if (trimmed.isEmpty()) return null
        val withScheme = if (trimmed.contains("://")) trimmed else "https://$trimmed"
        val host = try {
            URI(withScheme).host
        } catch (e: Exception) {
            null
        } ?: return null
        return host.lowercase().removePrefix("www.").ifEmpty { null }
    }

    /**
     * Logins for a request. With a trusted [webDomain] the entry's website must be exactly that host
     * (subdomains are different sites: a.github.io is not b.github.io). Otherwise the entry must be
     * linked to the requesting app's package.
     */
    fun find(entries: List<Entry>, pkg: String, webDomain: String?): List<Entry> {
        if (webDomain != null) {
            val host = hostOf(webDomain) ?: return emptyList()
            return entries.filter { hostOf(it.url) == host }
        }
        return entries.filter { pkg in it.apps }
    }
}
