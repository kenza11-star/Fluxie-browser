package com.fluxie.browser.common

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

enum class BlockReason { USER_LIST, ADULT_LIST, AD_TRACKER }

/** Immutable snapshot of everything the request filter needs; swapped atomically on change. */
data class BlockRules(
    val userDomains: Set<String> = emptySet(),
    val adultDomains: Set<String> = emptySet(),
    val adTrackerDomains: Set<String> = emptySet(),
    val adultFilterEnabled: Boolean = false,
    val adBlockEnabled: Boolean = true
)

object ContentBlocker {

    /**
     * Why [url] must not be loaded, or null if it may. The user's own list always applies; the adult
     * list only while the adult filter is on; the ad/tracker list only for sub-resources (a page the
     * user navigates to on purpose is never blocked as an "ad").
     */
    fun decide(url: String, rules: BlockRules, isMainFrame: Boolean): BlockReason? {
        val host = DomainRules.hostOf(url) ?: return null
        if (DomainRules.matches(host, rules.userDomains)) return BlockReason.USER_LIST
        if (rules.adultFilterEnabled && DomainRules.matches(host, rules.adultDomains)) return BlockReason.ADULT_LIST
        if (!isMainFrame && rules.adBlockEnabled && DomainRules.matches(host, rules.adTrackerDomains)) {
            return BlockReason.AD_TRACKER
        }
        return null
    }
}

/** Thread-safe per-tab counters of requests blocked on the current page (requests come from WebView threads). */
class BlockedCounts {
    private val counts = ConcurrentHashMap<Int, AtomicInteger>()

    fun increment(tabId: Int): Int = counts.computeIfAbsent(tabId) { AtomicInteger() }.incrementAndGet()

    fun get(tabId: Int): Int = counts[tabId]?.get() ?: 0

    fun reset(tabId: Int) {
        counts.remove(tabId)
    }
}

/** HTML shown in place of a blocked page. Everything inserted is escaped. */
object BlockPage {
    fun html(host: String, reasonLabel: String, title: String, detail: String): String {
        fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&#39;")
        return "<!DOCTYPE html><html><head><meta charset=\"utf-8\">" +
            "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
            "<title>${esc(title)}</title><style>" +
            "body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;" +
            "background:#0a0a0b;color:#f2f2f3;font-family:sans-serif;text-align:center}" +
            ".c{max-width:420px;padding:32px}h1{font-size:22px}p{color:#9a9aa0;line-height:1.5}" +
            "code{background:#1c1c1f;padding:2px 8px;border-radius:8px;color:#f2f2f3}" +
            "</style></head><body><div class=\"c\"><h1>${esc(title)}</h1>" +
            "<p><code>${esc(host)}</code></p><p>${esc(reasonLabel)}</p><p>${esc(detail)}</p></div></body></html>"
    }
}
