package com.fluxie.browser.lite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiteTabListTest {

    @Test fun startsWithOneActiveTab() {
        val t = LiteTabList()
        assertEquals(listOf(0), t.ids)
        assertEquals(0, t.activeId)
        assertTrue(t.canAdd)
    }

    @Test fun addActivatesNewTabUpToThree() {
        val t = LiteTabList()
        assertEquals(1, t.add())
        assertEquals(2, t.add())
        assertEquals(2, t.activeId)
        assertFalse(t.canAdd)
        assertNull(t.add())
        assertEquals(3, t.size)
    }

    @Test fun activateOnlyKnownIds() {
        val t = LiteTabList().also { it.add() }
        assertTrue(t.activate(0))
        assertEquals(0, t.activeId)
        assertFalse(t.activate(99))
        assertEquals(0, t.activeId)
    }

    @Test fun closingActiveTabActivatesLeftNeighbour() {
        val t = LiteTabList().also { it.add(); it.add() } // ids 0,1,2 active 2
        assertEquals(1, t.close(2))
        assertEquals(listOf(0, 1), t.ids)
        t.activate(0)
        assertEquals(1, t.close(0)) // first tab closed -> next one
        assertEquals(listOf(1), t.ids)
    }

    @Test fun closingBackgroundTabKeepsActive() {
        val t = LiteTabList().also { it.add(); it.add() }
        assertEquals(2, t.close(0))
        assertEquals(listOf(1, 2), t.ids)
    }

    @Test fun closingLastTabCreatesFreshOne() {
        val t = LiteTabList()
        val active = t.close(0)
        assertEquals(1, active)
        assertEquals(listOf(1), t.ids)
    }

    @Test fun closingUnknownIdChangesNothing() {
        val t = LiteTabList().also { it.add() }
        assertEquals(1, t.close(42))
        assertEquals(listOf(0, 1), t.ids)
    }

    @Test fun closedSlotCanBeReused() {
        val t = LiteTabList().also { it.add(); it.add() }
        t.close(1)
        assertTrue(t.canAdd)
        assertEquals(3, t.add()) // ids never reused
    }
}
