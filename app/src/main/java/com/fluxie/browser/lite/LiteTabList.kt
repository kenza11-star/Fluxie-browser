package com.fluxie.browser.lite

/**
 * Tab bookkeeping for Lite mode: at most [maxTabs] tabs, never zero. Pure logic (no Android types);
 * the activity keeps one WebView per id.
 */
class LiteTabList(val maxTabs: Int = MAX_TABS, firstId: Int = 0) {

    private val _ids = mutableListOf(firstId)
    private var nextId = firstId + 1

    val ids: List<Int> get() = _ids
    val size: Int get() = _ids.size
    var activeId: Int = firstId
        private set
    val canAdd: Boolean get() = _ids.size < maxTabs

    /** Opens and activates a new tab. Returns its id, or null when [maxTabs] is reached. */
    fun add(): Int? {
        if (!canAdd) return null
        val id = nextId++
        _ids.add(id)
        activeId = id
        return id
    }

    fun activate(id: Int): Boolean {
        if (id !in _ids) return false
        activeId = id
        return true
    }

    /**
     * Closes [id]. The neighbour to its left (or the first tab) becomes active. Closing the only tab
     * replaces it with a fresh one so the browser is never left without a tab.
     * Returns the id that is active afterwards; unknown ids change nothing.
     */
    fun close(id: Int): Int {
        val index = _ids.indexOf(id)
        if (index < 0) return activeId
        _ids.removeAt(index)
        if (_ids.isEmpty()) {
            val fresh = nextId++
            _ids.add(fresh)
            activeId = fresh
        } else if (activeId == id) {
            activeId = _ids[(index - 1).coerceAtLeast(0)]
        }
        return activeId
    }

    companion object {
        const val MAX_TABS = 3
    }
}
