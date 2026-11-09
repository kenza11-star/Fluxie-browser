package com.fluxie.browser.common

import org.junit.Assert.assertEquals
import org.junit.Test

class UrlUtilsTest {

    @Test
    fun `http URL is returned as-is`() {
        val result = UrlUtils.resolveInput("http://example.com", SearchEngine.GOOGLE)
        assertEquals("http://example.com", result)
    }

    @Test
    fun `https URL is returned as-is`() {
        val result = UrlUtils.resolveInput("https://example.com", SearchEngine.GOOGLE)
        assertEquals("https://example.com", result)
    }

    @Test
    fun `https URL with path is returned as-is`() {
        val result = UrlUtils.resolveInput("https://example.com/path?q=1", SearchEngine.BING)
        assertEquals("https://example.com/path?q=1", result)
    }

    @Test
    fun `http URL with port is returned as-is`() {
        val result = UrlUtils.resolveInput("http://localhost:8080", SearchEngine.GOOGLE)
        assertEquals("http://localhost:8080", result)
    }

    @Test
    fun `each search engine produces correct prefix`() {
        for (engine in SearchEngine.entries) {
            val result = UrlUtils.resolveInput("http://test", engine)
            assertEquals("http://test", result)
        }
    }

    // simplifyForDisplay tests
    @Test
    fun `simplifyForDisplay strips https`() {
        assertEquals("example.com", UrlUtils.simplifyForDisplay("https://example.com"))
    }

    @Test
    fun `simplifyForDisplay strips http`() {
        assertEquals("example.com", UrlUtils.simplifyForDisplay("http://example.com"))
    }

    @Test
    fun `simplifyForDisplay strips www prefix`() {
        assertEquals("example.com", UrlUtils.simplifyForDisplay("https://www.example.com"))
    }

    @Test
    fun `simplifyForDisplay strips m prefix`() {
        assertEquals("example.com", UrlUtils.simplifyForDisplay("https://m.example.com"))
    }

    @Test
    fun `simplifyForDisplay strips trailing slash`() {
        assertEquals("example.com", UrlUtils.simplifyForDisplay("https://example.com/"))
    }

    @Test
    fun `simplifyForDisplay keeps path after slash`() {
        assertEquals("example.com/page", UrlUtils.simplifyForDisplay("https://example.com/page"))
    }

    @Test
    fun `simplifyForDisplay handles empty string`() {
        assertEquals("", UrlUtils.simplifyForDisplay(""))
    }

    // ---- resolveInput: normalization ----
    private val google = SearchEngine.GOOGLE

    @Test
    fun `blank input resolves to empty string`() {
        assertEquals("", UrlUtils.resolveInput("", google))
        assertEquals("", UrlUtils.resolveInput("   ", google))
    }

    @Test
    fun `input is trimmed`() {
        assertEquals("https://example.com", UrlUtils.resolveInput("  https://example.com \n", google))
        assertEquals("https://example.com", UrlUtils.resolveInput("  example.com ", google))
    }

    @Test
    fun `scheme is matched case-insensitively`() {
        assertEquals("HTTPS://Example.com", UrlUtils.resolveInput("HTTPS://Example.com", google))
    }

    @Test
    fun `bare domain gets https`() {
        assertEquals("https://example.com", UrlUtils.resolveInput("example.com", google))
        assertEquals("https://www.example.co.uk", UrlUtils.resolveInput("www.example.co.uk", google))
    }

    @Test
    fun `bare domain keeps path query and fragment`() {
        assertEquals("https://example.com/a/b?x=1#top", UrlUtils.resolveInput("example.com/a/b?x=1#top", google))
        assertEquals("https://example.com?q=1", UrlUtils.resolveInput("example.com?q=1", google))
    }

    @Test
    fun `bare domain with port gets https`() {
        assertEquals("https://example.com:8443/x", UrlUtils.resolveInput("example.com:8443/x", google))
    }

    @Test
    fun `localhost and IP addresses get http`() {
        assertEquals("http://localhost", UrlUtils.resolveInput("localhost", google))
        assertEquals("http://localhost:3000/app", UrlUtils.resolveInput("localhost:3000/app", google))
        assertEquals("http://192.168.1.5:8080", UrlUtils.resolveInput("192.168.1.5:8080", google))
        assertEquals("http://[::1]:8080/x", UrlUtils.resolveInput("[::1]:8080/x", google))
    }

    @Test
    fun `invalid IPv4 is searched instead`() {
        assertEquals(
            "https://www.google.com/search?q=999.1.1.1",
            UrlUtils.resolveInput("999.1.1.1", google)
        )
    }

    @Test
    fun `plain words are searched`() {
        assertEquals("https://www.google.com/search?q=kotlin", UrlUtils.resolveInput("kotlin", google))
    }

    @Test
    fun `phrases are url-encoded for search`() {
        assertEquals(
            "https://duckduckgo.com/?q=cats+%26+dogs%3F",
            UrlUtils.resolveInput("cats & dogs?", SearchEngine.DUCKDUCKGO)
        )
        assertEquals(
            "https://www.bing.com/search?q=caf%C3%A9+paris",
            UrlUtils.resolveInput("café paris", SearchEngine.BING)
        )
    }

    @Test
    fun `domain followed by words is a search not a url`() {
        assertEquals(
            "https://www.google.com/search?q=example.com+login",
            UrlUtils.resolveInput("example.com login", google)
        )
    }

    @Test
    fun `dangerous and unsupported schemes are searched not loaded`() {
        assertEquals(
            "https://www.google.com/search?q=javascript%3Aalert%281%29",
            UrlUtils.resolveInput("javascript:alert(1)", google)
        )
        assertEquals(
            "https://www.google.com/search?q=file%3A%2F%2F%2Fsdcard%2Fa.html",
            UrlUtils.resolveInput("file:///sdcard/a.html", google)
        )
    }

    @Test
    fun `email-like input is searched`() {
        assertEquals(
            "https://www.google.com/search?q=me%40example.com",
            UrlUtils.resolveInput("me@example.com", google)
        )
    }

    @Test
    fun `single label with trailing dot or bad tld is searched`() {
        assertEquals("https://www.google.com/search?q=version+1.2", UrlUtils.resolveInput("version 1.2", google))
        assertEquals("https://www.google.com/search?q=a.1", UrlUtils.resolveInput("a.1", google))
    }

    @Test
    fun `search engine prefix is used per engine`() {
        for (engine in SearchEngine.entries) {
            assertEquals(engine.searchUrl + "hello", UrlUtils.resolveInput("hello", engine))
        }
    }

    // ---- extractDomain ----
    @Test
    fun `extractDomain strips scheme path and www`() {
        assertEquals("example.com", UrlUtils.extractDomain("https://www.example.com/a/b?x=1#f"))
    }

    @Test
    fun `extractDomain keeps real subdomains`() {
        assertEquals("docs.example.com", UrlUtils.extractDomain("https://docs.example.com/x"))
    }

    @Test
    fun `extractDomain strips m prefix only when a real domain remains`() {
        assertEquals("example.com", UrlUtils.extractDomain("https://m.example.com"))
        assertEquals("m.com", UrlUtils.extractDomain("https://m.com"))
    }

    @Test
    fun `extractDomain drops port and userinfo`() {
        assertEquals("example.com", UrlUtils.extractDomain("https://user:pw@example.com:8443/x"))
        assertEquals("localhost", UrlUtils.extractDomain("http://localhost:3000"))
    }

    @Test
    fun `extractDomain handles IPv6 and blank`() {
        assertEquals("[::1]", UrlUtils.extractDomain("http://[::1]:8080/x"))
        assertEquals("", UrlUtils.extractDomain(""))
    }

    // ---- asUrlOrNull (homepage validation) ----
    @Test
    fun `asUrlOrNull accepts urls and host-like input`() {
        assertEquals("https://example.com", UrlUtils.asUrlOrNull("https://example.com"))
        assertEquals("https://example.com", UrlUtils.asUrlOrNull(" example.com "))
        assertEquals("http://localhost:3000", UrlUtils.asUrlOrNull("localhost:3000"))
    }

    @Test
    fun `asUrlOrNull rejects search text and empty hosts`() {
        assertEquals(null, UrlUtils.asUrlOrNull(""))
        assertEquals(null, UrlUtils.asUrlOrNull("hello world"))
        assertEquals(null, UrlUtils.asUrlOrNull("kotlin"))
        assertEquals(null, UrlUtils.asUrlOrNull("http://"))
        assertEquals(null, UrlUtils.asUrlOrNull("javascript:alert(1)"))
    }

    @Test
    fun `resolveInput accepts a custom template`() {
        assertEquals(
            "https://example.com/find?term=a+b",
            UrlUtils.resolveInput("a b", "https://example.com/find?term=%s")
        )
    }
}
