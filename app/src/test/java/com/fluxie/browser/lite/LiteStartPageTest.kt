package com.fluxie.browser.lite

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiteStartPageTest {

    @Test fun listsBookmarksAsEscapedLinks() {
        val html = LiteStartPage.html("Fluxie Lite", "sub", "none", listOf(LiteBookmark("<b>X</b> & Y", "https://e.com/?a=1&b=\"2\"")))
        assertTrue(html.contains("&lt;b&gt;X&lt;/b&gt; &amp; Y"))
        assertTrue(html.contains("href=\"https://e.com/?a=1&amp;b=&quot;2&quot;\""))
        assertFalse(html.contains("<b>X"))
    }

    @Test fun emptyShowsMessage() {
        val html = LiteStartPage.html("T", "s", "No bookmarks yet", emptyList())
        assertTrue(html.contains("No bookmarks yet"))
    }

    @Test fun nonWebUrlsAreNotLinked() {
        val html = LiteStartPage.html("T", "s", "empty", listOf(LiteBookmark("js", "javascript:alert(1)")))
        assertFalse(html.contains("javascript:"))
        assertTrue(html.contains("empty"))
    }
}
