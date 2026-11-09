package com.fluxie.browser.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainRulesTest {

    @Test fun normalizeAcceptsCommonForms() {
        assertEquals("example.com", DomainRules.normalize("example.com"))
        assertEquals("example.com", DomainRules.normalize("  EXAMPLE.com  "))
        assertEquals("example.com", DomainRules.normalize("*.example.com"))
        assertEquals("example.com", DomainRules.normalize(".example.com"))
        assertEquals("example.com", DomainRules.normalize("https://example.com/a/b?x=1#f"))
        assertEquals("example.com", DomainRules.normalize("example.com:8080"))
        assertEquals("example.com", DomainRules.normalize("example.com."))
        assertEquals("ads.example.co.uk", DomainRules.normalize("ads.example.co.uk"))
        assertEquals("xn--bcher-kva.de", DomainRules.normalize("bücher.de"))
    }

    @Test fun normalizeRejectsNonDomains() {
        listOf("", "   ", "localhost", "example", "192.168.1.1", "exa mple.com", "-a.com", "a-.com",
            "example.c", "example.123", "http://", "example.com:abc", "a..com", "user@").forEach {
            assertNull("should reject '$it'", DomainRules.normalize(it))
        }
    }

    @Test fun hostOfOnlyForHttpUrls() {
        assertEquals("example.com", DomainRules.hostOf("https://Example.com/path"))
        assertEquals("a.example.com", DomainRules.hostOf("http://user:pw@a.example.com:81/x"))
        assertNull(DomainRules.hostOf("ftp://example.com"))
        assertNull(DomainRules.hostOf("javascript:alert(1)"))
        assertNull(DomainRules.hostOf("about:blank"))
        assertNull(DomainRules.hostOf("http://[::1]/"))
        assertNull(DomainRules.hostOf("https://"))
    }

    @Test fun matchesDomainAndSubdomainsOnly() {
        val rules = setOf("example.com", "ads.other.net")
        assertTrue(DomainRules.matches("example.com", rules))
        assertTrue(DomainRules.matches("www.example.com", rules))
        assertTrue(DomainRules.matches("a.b.example.com", rules))
        assertTrue(DomainRules.matches("ads.other.net", rules))
        assertFalse(DomainRules.matches("notexample.com", rules))
        assertFalse(DomainRules.matches("example.com.evil.org", rules))
        assertFalse(DomainRules.matches("other.net", rules))
        assertFalse(DomainRules.matches("example.com", emptySet()))
    }
}
