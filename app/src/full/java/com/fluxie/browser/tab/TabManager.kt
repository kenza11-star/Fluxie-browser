package com.fluxie.browser.tab

import android.os.Bundle

/** What is needed to bring a closed tab back. [webState] carries the WebView back/forward list. */
class ClosedTab(
    val url: String,
    val title: String,
    val index: Int,
    val webState: Bundle? = null
)

/**
 * Result of [TabManager.restoreClosedTab]. [replacedTabId] is set when the lone blank tab was
 * swapped out for the restored one, so the caller can destroy that tab's WebView.
 */
class RestoredTab(val tab: Tab, val closed: ClosedTab, val replacedTabId: Int?)

class TabManager {

    private val _tabs = mutableListOf(Tab(id = 0))
    val tabs: List<Tab> get() = _tabs

    private var _activeTabIndex = 0
    val activeTabIndex: Int get() = _activeTabIndex
    val activeTab: Tab get() = _tabs[_activeTabIndex]

    val tabCount: Int get() = _tabs.size
    val incognitoTabCount: Int get() = _tabs.count { it.isIncognito }

    private val closedTabs = ArrayDeque<ClosedTab>()
    val canRestoreClosedTab: Boolean get() = closedTabs.isNotEmpty()

    private var nextId = 1

    fun addTab(incognito: Boolean = false): Tab {
        val tab = Tab(id = nextId++, isIncognito = incognito)
        _tabs.add(tab)
        _activeTabIndex = _tabs.size - 1
        return tab
    }

    fun switchTo(index: Int) {
        require(index in _tabs.indices)
        _activeTabIndex = index
    }

    /**
     * Closes the tab at [index]. Returns a fresh blank tab if the last tab was closed, else null.
     * Normal tabs that had a page loaded are remembered for [restoreClosedTab]; incognito tabs
     * and blank start-page tabs never are.
     */
    fun closeTab(index: Int, webState: Bundle? = null): Tab? {
        require(index in _tabs.indices)
        val closedTab = _tabs.removeAt(index)
        if (!closedTab.isIncognito && closedTab.url.isNotEmpty()) {
            closedTabs.addLast(ClosedTab(closedTab.url, closedTab.title, index, webState))
            while (closedTabs.size > MAX_CLOSED_TABS) closedTabs.removeFirst()
        }
        if (_tabs.isEmpty()) {
            val newTab = Tab(id = nextId++)
            _tabs.add(newTab)
            _activeTabIndex = 0
            return newTab
        }
        // Closing a tab before the active one shifts it down; closing the active one
        // keeps the index (the next tab slides in) and only needs clamping at the end.
        if (index < _activeTabIndex) {
            _activeTabIndex--
        } else if (_activeTabIndex >= _tabs.size) {
            _activeTabIndex = _tabs.size - 1
        }
        return null
    }

    /**
     * Re-creates the most recently closed tab at its old position (clamped) and makes it active.
     * If the only open tab is an untouched blank tab it is replaced instead of left behind.
     */
    fun restoreClosedTab(): RestoredTab? {
        val closed = closedTabs.removeLastOrNull() ?: return null

        var replacedId: Int? = null
        val only = _tabs.singleOrNull()
        if (only != null && !only.isIncognito && only.url.isEmpty()) {
            replacedId = only.id
            _tabs.clear()
        }

        val tab = Tab(id = nextId++, url = closed.url, title = closed.title)
        val position = closed.index.coerceIn(0, _tabs.size)
        _tabs.add(position, tab)
        _activeTabIndex = position
        return RestoredTab(tab, closed, replacedId)
    }

    fun indexOfId(tabId: Int): Int = _tabs.indexOfFirst { it.id == tabId }

    companion object {
        const val MAX_CLOSED_TABS = 10
    }
}
