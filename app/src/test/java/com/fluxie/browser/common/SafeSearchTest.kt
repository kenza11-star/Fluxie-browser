package com.fluxie.browser.common

import org.junit.Assert.assertEquals
import org.junit.Test

class SafeSearchTest {

    @Test fun googleAddsSafeActive() {
        assertEquals("https://www.google.com/search?q=cats&safe=active",
            SafeSearch.apply("https://www.google.com/search?q=cats"))
        assertEquals("https://www.google.co.uk/search?q=cats&safe=active",
            SafeSearch.apply("https://www.google.co.uk/search?q=cats"))
        assertEquals("https://www.google.co.id/search?q=a&safe=active",
            SafeSearch.apply("https://www.google.co.id/search?q=a"))
    }

    @Test fun overridesAnExplicitOffValue() {
        assertEquals("https://www.google.com/search?q=cats&safe=active",
            SafeSearch.apply("https://www.google.com/search?safe=off&q=cats"))
        assertEquals("https://www.bing.com/search?q=x&adlt=strict",
            SafeSearch.apply("https://www.bing.com/search?q=x&adlt=off"))
    }

    @Test fun alreadySafeIsUnchanged() {
        val url = "https://www.google.com/search?q=cats&safe=active"
        assertEquals(url, SafeSearch.apply(url))
    }

    @Test fun bingDuckBrave() {
        assertEquals("https://www.bing.com/search?q=cats&adlt=strict", SafeSearch.apply("https://www.bing.com/search?q=cats"))
        assertEquals("https://duckduckgo.com/?q=cats&kp=1", SafeSearch.apply("https://duckduckgo.com/?q=cats"))
        assertEquals("https://search.brave.com/search?q=cats&safesearch=strict",
            SafeSearch.apply("https://search.brave.com/search?q=cats"))
    }

    @Test fun keepsFragmentAndOtherParams() {
        assertEquals("https://www.google.com/search?q=a%20b&hl=en&safe=active#top",
            SafeSearch.apply("https://www.google.com/search?q=a%20b&hl=en#top"))
    }

    @Test fun otherPagesAndSitesUntouched() {
        listOf(
            "https://www.google.com/maps?q=cats",
            "https://www.google.com/search",          // no query -> not a results page
            "https://www.google.com/search?hl=en",    // no q
            "https://example.com/search?q=cats",
            "https://notgoogle.com/search?q=cats",
            "https://www.google.com.evil.org/search?q=cats",
            "about:blank",
            "not a url"
        ).forEach { assertEquals(it, SafeSearch.apply(it)) }
    }

    @Test fun idempotent() {
        val once = SafeSearch.apply("https://duckduckgo.com/?q=cats&kp=-2")
        assertEquals("https://duckduckgo.com/?q=cats&kp=1", once)
        assertEquals(once, SafeSearch.apply(once))
    }
}
