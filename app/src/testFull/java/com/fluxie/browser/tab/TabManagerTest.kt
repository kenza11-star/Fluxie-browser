package com.fluxie.browser.tab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class TabManagerTest {

    private lateinit var tabManager: TabManager

    @Before
    fun setUp() {
        tabManager = TabManager()
    }

    @Test
    fun `initial state has one tab`() {
        assertEquals(1, tabManager.tabCount)
        assertEquals(0, tabManager.activeTabIndex)
    }

    @Test
    fun `addTab increases count and sets new tab as active`() {
        tabManager.addTab()
        assertEquals(2, tabManager.tabCount)
        assertEquals(1, tabManager.activeTabIndex)
    }

    @Test
    fun `switchTo changes active tab`() {
        tabManager.addTab()
        tabManager.addTab()
        tabManager.switchTo(0)
        assertEquals(0, tabManager.activeTabIndex)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `switchTo invalid index throws`() {
        tabManager.switchTo(5)
    }

    @Test
    fun `closeTab removes tab and adjusts active index`() {
        tabManager.addTab()
        tabManager.addTab()
        assertEquals(3, tabManager.tabCount)

        tabManager.switchTo(2)
        val newTab = tabManager.closeTab(2)
        assertNull(newTab)
        assertEquals(2, tabManager.tabCount)
        assertEquals(1, tabManager.activeTabIndex)
    }

    @Test
    fun `closing a tab before the active one keeps the same tab active`() {
        tabManager.addTab()
        tabManager.addTab()
        tabManager.switchTo(1)
        val stillActive = tabManager.activeTab.id

        tabManager.closeTab(0)

        assertEquals(2, tabManager.tabCount)
        assertEquals(0, tabManager.activeTabIndex)
        assertEquals(stillActive, tabManager.activeTab.id)
    }

    @Test
    fun `closing last tab auto-creates new tab`() {
        val newTab = tabManager.closeTab(0)
        assertNotNull(newTab)
        assertEquals(1, tabManager.tabCount)
        assertEquals(0, tabManager.activeTabIndex)
        assertEquals("", tabManager.activeTab.url)
    }

    @Test
    fun `tabs are independent`() {
        tabManager.activeTab.url = "https://first.com"
        tabManager.addTab()
        tabManager.activeTab.url = "https://second.com"

        tabManager.switchTo(0)
        assertEquals("https://first.com", tabManager.activeTab.url)

        tabManager.switchTo(1)
        assertEquals("https://second.com", tabManager.activeTab.url)
    }

    // ---- closing / restoring ----
    private fun loaded(url: String, title: String = url): Tab =
        tabManager.activeTab.also { it.url = url; it.title = title }

    @Test
    fun `nothing to restore initially`() {
        assertFalse(tabManager.canRestoreClosedTab)
        assertNull(tabManager.restoreClosedTab())
    }

    @Test
    fun `closed tab with a page can be restored and becomes active`() {
        loaded("https://a.com")
        tabManager.addTab()
        loaded("https://b.com", "B")
        tabManager.closeTab(1)
        assertTrue(tabManager.canRestoreClosedTab)

        val restored = tabManager.restoreClosedTab()!!

        assertEquals("https://b.com", restored.tab.url)
        assertEquals("B", restored.tab.title)
        assertEquals(2, tabManager.tabCount)
        assertEquals(1, tabManager.activeTabIndex)
        assertEquals(restored.tab.id, tabManager.activeTab.id)
        assertFalse(tabManager.canRestoreClosedTab)
    }

    @Test
    fun `restored tab goes back to its old position`() {
        loaded("https://a.com")
        tabManager.addTab(); loaded("https://b.com")
        tabManager.addTab(); loaded("https://c.com")
        tabManager.closeTab(1) // close b, in the middle

        tabManager.restoreClosedTab()

        assertEquals(listOf("https://a.com", "https://b.com", "https://c.com"), tabManager.tabs.map { it.url })
        assertEquals(1, tabManager.activeTabIndex)
    }

    @Test
    fun `restore position is clamped when fewer tabs remain`() {
        loaded("https://a.com")
        tabManager.addTab()                 // blank, never remembered
        tabManager.addTab(); loaded("https://b.com")
        tabManager.closeTab(2)              // b was at index 2
        tabManager.closeTab(1)              // drop the blank; only a.com is left

        val b = tabManager.restoreClosedTab()!!

        assertEquals("https://b.com", b.tab.url)
        assertEquals(listOf("https://a.com", "https://b.com"), tabManager.tabs.map { it.url })
        assertEquals(1, tabManager.activeTabIndex)
    }

    @Test
    fun `restores most recently closed first`() {
        loaded("https://a.com")
        tabManager.addTab(); loaded("https://b.com")
        tabManager.addTab(); loaded("https://c.com")
        tabManager.closeTab(2)
        tabManager.closeTab(1)

        assertEquals("https://b.com", tabManager.restoreClosedTab()!!.tab.url)
        assertEquals("https://c.com", tabManager.restoreClosedTab()!!.tab.url)
        assertNull(tabManager.restoreClosedTab())
    }

    @Test
    fun `restored tab gets a new id`() {
        loaded("https://a.com")
        val oldId = tabManager.activeTab.id
        tabManager.addTab()
        tabManager.closeTab(0)
        assertTrue(tabManager.restoreClosedTab()!!.tab.id != oldId)
    }

    @Test
    fun `blank tabs are not remembered`() {
        tabManager.addTab() // blank
        tabManager.closeTab(1)
        assertFalse(tabManager.canRestoreClosedTab)
    }

    @Test
    fun `incognito tabs are never remembered`() {
        tabManager.addTab(incognito = true)
        loaded("https://secret.example")
        tabManager.closeTab(1)
        assertFalse(tabManager.canRestoreClosedTab)
        assertNull(tabManager.restoreClosedTab())
    }

    @Test
    fun `closing the last tab remembers it and restore replaces the blank replacement`() {
        loaded("https://only.com")
        val blank = tabManager.closeTab(0)!!

        val restored = tabManager.restoreClosedTab()!!

        assertEquals(blank.id, restored.replacedTabId)
        assertEquals(1, tabManager.tabCount)
        assertEquals("https://only.com", tabManager.activeTab.url)
    }

    @Test
    fun `restore does not replace a blank tab when other tabs exist`() {
        loaded("https://a.com")
        tabManager.addTab(); loaded("https://b.com")
        tabManager.closeTab(1)
        tabManager.addTab() // blank, plus a.com
        val restored = tabManager.restoreClosedTab()!!
        assertNull(restored.replacedTabId)
        assertEquals(3, tabManager.tabCount)
    }

    @Test
    fun `restore does not replace a lone incognito tab`() {
        loaded("https://a.com")
        tabManager.addTab(incognito = true)
        tabManager.closeTab(0)
        val restored = tabManager.restoreClosedTab()!!
        assertNull(restored.replacedTabId)
        assertEquals(2, tabManager.tabCount)
    }

    @Test
    fun `closed tab history is capped`() {
        repeat(TabManager.MAX_CLOSED_TABS + 5) { i ->
            tabManager.addTab()
            loaded("https://site$i.com")
            tabManager.closeTab(tabManager.activeTabIndex)
        }
        var count = 0
        var newest: String? = null
        while (true) {
            val r = tabManager.restoreClosedTab() ?: break
            if (newest == null) newest = r.tab.url
            count++
        }
        assertEquals(TabManager.MAX_CLOSED_TABS, count)
        assertEquals("https://site${TabManager.MAX_CLOSED_TABS + 4}.com", newest)
    }

    // ---- incognito bookkeeping ----
    @Test
    fun `addTab incognito flags the tab and counts it`() {
        val t = tabManager.addTab(incognito = true)
        assertTrue(t.isIncognito)
        assertEquals(1, tabManager.incognitoTabCount)
        assertFalse(tabManager.tabs[0].isIncognito)
    }

    @Test
    fun `incognito count drops when incognito tab closes`() {
        tabManager.addTab(incognito = true)
        tabManager.addTab(incognito = true)
        tabManager.closeTab(1)
        assertEquals(1, tabManager.incognitoTabCount)
        tabManager.closeTab(1)
        assertEquals(0, tabManager.incognitoTabCount)
    }

    @Test
    fun `closing the last tab when it is incognito gives a normal blank tab`() {
        tabManager.closeTab(0) // replace initial tab with a fresh one
        tabManager.addTab(incognito = true)
        tabManager.closeTab(0)
        val blank = tabManager.closeTab(0)!!
        assertFalse(blank.isIncognito)
        assertEquals(0, tabManager.incognitoTabCount)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `closeTab invalid index throws`() {
        tabManager.closeTab(3)
    }

    @Test
    fun `switching and closing keep the right tab active`() {
        loaded("https://a.com")
        tabManager.addTab(); loaded("https://b.com")
        tabManager.addTab(); loaded("https://c.com")
        tabManager.switchTo(0)
        tabManager.closeTab(2)
        assertEquals("https://a.com", tabManager.activeTab.url)
        tabManager.switchTo(1)
        tabManager.closeTab(1) // close active (last) -> previous becomes active
        assertEquals(0, tabManager.activeTabIndex)
        assertEquals("https://a.com", tabManager.activeTab.url)
    }
}
