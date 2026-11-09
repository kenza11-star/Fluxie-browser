package com.fluxie.browser.lite

import org.junit.Assert.assertEquals
import org.junit.Test

class LiteBookmarkCodecTest {

    @Test fun roundTripsPlainEntries() {
        val items = listOf(LiteBookmark("Example", "https://example.com/"), LiteBookmark("B", "http://b.org/x?y=1"))
        assertEquals(items, LiteBookmarkCodec.decode(LiteBookmarkCodec.encode(items)))
    }

    @Test fun roundTripsSpecialCharacters() {
        val items = listOf(LiteBookmark("a\tb\nc\\d\re", "https://x.com/?q=a\\b"))
        assertEquals(items, LiteBookmarkCodec.decode(LiteBookmarkCodec.encode(items)))
    }

    @Test fun roundTripsUnicodeAndEmptyTitle() {
        val items = listOf(LiteBookmark("日本語 ✓", "https://例え.jp/"), LiteBookmark("", "https://e.com"))
        assertEquals(items, LiteBookmarkCodec.decode(LiteBookmarkCodec.encode(items)))
    }

    @Test fun emptyAndNullDecodeToEmpty() {
        assertEquals(emptyList<LiteBookmark>(), LiteBookmarkCodec.decode(null))
        assertEquals(emptyList<LiteBookmark>(), LiteBookmarkCodec.decode(""))
        assertEquals("", LiteBookmarkCodec.encode(emptyList()))
    }

    @Test fun damagedLinesAreSkipped() {
        val text = "no tab here\nGood\thttps://good.com\n\t\nBlankUrl\t   \n"
        assertEquals(listOf(LiteBookmark("Good", "https://good.com")), LiteBookmarkCodec.decode(text))
    }

    @Test fun missingBookmarksKeepsOnlyUnknownAndDedupes() {
        val known = setOf("https://a.com")
        val incoming = listOf(
            LiteBookmark("A", "https://a.com"),
            LiteBookmark("B", "https://b.com"),
            LiteBookmark("B again", "https://b.com"),
            LiteBookmark("C", "https://c.com")
        )
        assertEquals(
            listOf(LiteBookmark("B", "https://b.com"), LiteBookmark("C", "https://c.com")),
            missingBookmarks(known, incoming)
        )
    }
}
