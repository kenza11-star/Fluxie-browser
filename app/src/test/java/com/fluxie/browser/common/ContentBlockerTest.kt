package com.fluxie.browser.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentBlockerTest {

    private val rules = BlockRules(
        userDomains = setOf("blocked.example"),
        adultDomains = setOf("adult.example"),
        adTrackerDomains = setOf("ads.example"),
        adultFilterEnabled = false,
        adBlockEnabled = true
    ).let { it.copy(userDomains = setOf("blocked.com"), adultDomains = setOf("adult.com"), adTrackerDomains = setOf("adserver.net")) }

    @Test fun userListBlocksMainFrameAndSubresources() {
        assertEquals(BlockReason.USER_LIST, ContentBlocker.decide("https://blocked.com/", rules, true))
        assertEquals(BlockReason.USER_LIST, ContentBlocker.decide("https://cdn.blocked.com/x.js", rules, false))
    }

    @Test fun adultListOnlyWhenFilterOn() {
        assertNull(ContentBlocker.decide("https://adult.com/", rules, true))
        val on = rules.copy(adultFilterEnabled = true)
        assertEquals(BlockReason.ADULT_LIST, ContentBlocker.decide("https://www.adult.com/", on, true))
    }

    @Test fun adTrackerOnlyForSubresourcesAndWhenEnabled() {
        assertEquals(BlockReason.AD_TRACKER, ContentBlocker.decide("https://adserver.net/p.js", rules, false))
        assertNull(ContentBlocker.decide("https://adserver.net/", rules, true))
        assertNull(ContentBlocker.decide("https://adserver.net/p.js", rules.copy(adBlockEnabled = false), false))
    }

    @Test fun userListWinsOverOtherReasons() {
        val r = rules.copy(userDomains = setOf("adserver.net"))
        assertEquals(BlockReason.USER_LIST, ContentBlocker.decide("https://adserver.net/p.js", r, false))
    }

    @Test fun unlistedAndNonHttpAllowed() {
        assertNull(ContentBlocker.decide("https://example.org/", rules, true))
        assertNull(ContentBlocker.decide("data:text/html,hi", rules, false))
        assertNull(ContentBlocker.decide("https://notblocked.com/", rules, true))
    }

    @Test fun countsArePerTabAndReset() {
        val c = BlockedCounts()
        assertEquals(0, c.get(1))
        assertEquals(1, c.increment(1))
        assertEquals(2, c.increment(1))
        assertEquals(1, c.increment(2))
        c.reset(1)
        assertEquals(0, c.get(1))
        assertEquals(1, c.get(2))
    }

    @Test fun blockPageEscapesHtml() {
        val html = BlockPage.html("<script>x</script>.com", "R&D", "T\"", "d'")
        assertTrue(!html.contains("<script>x"))
        assertTrue(html.contains("&lt;script&gt;"))
        assertTrue(html.contains("R&amp;D"))
    }
}
