package com.fluxie.browser.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchEngineTest {

    @Test
    fun `offers google duckduckgo brave and bing`() {
        assertEquals(
            listOf("Google", "DuckDuckGo", "Brave Search", "Bing"),
            SearchEngine.entries.map { it.displayName }
        )
    }

    @Test
    fun `fromName finds engines and rejects unknown names`() {
        assertEquals(SearchEngine.BRAVE, SearchEngine.fromName("BRAVE"))
        assertNull(SearchEngine.fromName("CUSTOM"))
        assertNull(SearchEngine.fromName(null))
        assertNull(SearchEngine.fromName("brave")) // names are stored exactly
    }

    @Test
    fun `every engine is an https url ending in a query parameter`() {
        for (engine in SearchEngine.entries) {
            assertTrue(engine.name, engine.searchUrl.startsWith("https://"))
            assertTrue(engine.name, engine.searchUrl.endsWith("="))
            assertEquals(engine.searchUrl + "%s", engine.template)
        }
    }

    // ---- template validation ----
    @Test
    fun `valid templates`() {
        assertTrue(SearchTemplate.isValid("https://example.com/search?q=%s"))
        assertTrue(SearchTemplate.isValid("http://localhost:8080/?query=%s&lang=en"))
        assertTrue(SearchTemplate.isValid("  https://example.com/%s  "))
    }

    @Test
    fun `invalid templates`() {
        assertFalse(SearchTemplate.isValid(""))
        assertFalse(SearchTemplate.isValid("https://example.com/search?q="))     // no placeholder
        assertFalse(SearchTemplate.isValid("example.com/search?q=%s"))           // no scheme
        assertFalse(SearchTemplate.isValid("ftp://example.com/?q=%s"))
        assertFalse(SearchTemplate.isValid("https://%s"))                        // placeholder as host
        assertFalse(SearchTemplate.isValid("https://%s.example.com/"))
        assertFalse(SearchTemplate.isValid("https:///search?q=%s"))              // empty host
        assertFalse(SearchTemplate.isValid("https://example.com/a b?q=%s"))      // whitespace
    }

    @Test
    fun `build encodes the query and fills every placeholder`() {
        assertEquals(
            "https://example.com/s?q=a+%26+b&again=a+%26+b",
            SearchTemplate.build("https://example.com/s?q=%s&again=%s", "a & b")
        )
    }

    // ---- choice resolution ----
    @Test
    fun `preset choice resolves to its template`() {
        assertEquals(SearchEngine.DUCKDUCKGO.template, resolveSearchTemplate("DUCKDUCKGO", "ignored"))
    }

    @Test
    fun `unknown or missing choice falls back to google`() {
        assertEquals(SearchEngine.GOOGLE.template, resolveSearchTemplate(null, null))
        assertEquals(SearchEngine.GOOGLE.template, resolveSearchTemplate("NOPE", null))
    }

    @Test
    fun `custom choice uses the template when valid`() {
        assertEquals(
            "https://example.com/?q=%s",
            resolveSearchTemplate(CUSTOM_SEARCH, "  https://example.com/?q=%s ")
        )
    }

    @Test
    fun `custom choice with invalid or missing template falls back to google`() {
        assertEquals(SearchEngine.GOOGLE.template, resolveSearchTemplate(CUSTOM_SEARCH, "not a template"))
        assertEquals(SearchEngine.GOOGLE.template, resolveSearchTemplate(CUSTOM_SEARCH, null))
    }

    @Test
    fun `display name is the engine name or the custom domain`() {
        assertEquals("Brave Search", searchDisplayName("BRAVE", null))
        assertEquals("example.com", searchDisplayName(CUSTOM_SEARCH, "https://www.example.com/s?q=%s"))
        assertEquals("Google", searchDisplayName(CUSTOM_SEARCH, "broken"))
    }

    @Test
    fun `resolved choice drives the final search url`() {
        val template = resolveSearchTemplate(CUSTOM_SEARCH, "https://example.com/find/%s")
        assertEquals("https://example.com/find/hello+world", UrlUtils.resolveInput("hello world", template))
        assertEquals(
            "https://search.brave.com/search?q=hello+world",
            UrlUtils.resolveInput("hello world", resolveSearchTemplate("BRAVE", null))
        )
    }
}
