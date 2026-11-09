package com.fluxie.browser.common

import org.junit.Assert.assertEquals
import org.junit.Test

class BlocklistParserTest {

    @Test fun plainDomainsCommentsAndBlanks() {
        val r = BlocklistParser.parse(
            """
            # my list
            example.com
            
            ! another comment style
            *.Tracker.net   # trailing comment
            https://site.org/
            """.trimIndent()
        )
        assertEquals(listOf("example.com", "tracker.net", "site.org"), r.domains)
        assertEquals(0, r.skippedLines)
    }

    @Test fun hostsFileFormat() {
        val r = BlocklistParser.parse("0.0.0.0 ads.example.com\n127.0.0.1 a.com b.com\n::1 c.org\n")
        assertEquals(listOf("ads.example.com", "a.com", "b.com", "c.org"), r.domains)
        assertEquals(0, r.skippedLines)
    }

    @Test fun adblockDomainAnchorsOnly() {
        val r = BlocklistParser.parse("||ads.example.com^\n||example.org^\n")
        assertEquals(listOf("ads.example.com", "example.org"), r.domains)
        assertEquals(0, r.skippedLines)
    }

    @Test fun unsupportedRulesAreSkippedNotGuessed() {
        val r = BlocklistParser.parse(
            "||example.com/ads/banner^\n@@||allowed.com^\nexample.com##.ad\nexample.com/ads\n/banner/*\n0.0.0.0 localhost\nnot a domain\ngood.com\n"
        )
        assertEquals(listOf("good.com"), r.domains)
        assertEquals(7, r.skippedLines)
    }

    @Test fun duplicatesCollapseAndBomIgnored() {
        val r = BlocklistParser.parse("﻿example.com\nEXAMPLE.com\n0.0.0.0 example.com\n")
        assertEquals(listOf("example.com"), r.domains)
    }

    @Test fun windowsLineEndings() {
        val r = BlocklistParser.parse("a.com\r\nb.com\r\n")
        assertEquals(listOf("a.com", "b.com"), r.domains)
    }

    @Test fun emptyInput() {
        val r = BlocklistParser.parse("")
        assertEquals(emptyList<String>(), r.domains)
        assertEquals(0, r.skippedLines)
    }
}
