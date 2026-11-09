package com.fluxie.browser.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class HistoryGroupingTest {

    private val zone = TimeZone.getTimeZone("UTC")

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12): Long =
        Calendar.getInstance(zone).apply {
            clear(); set(year, month, day, hour, 0, 0)
        }.timeInMillis

    // "Now" = 2026-11-08 15:00 UTC
    private val now = at(2026, Calendar.NOVEMBER, 8, 15)

    private fun group(vararg times: Long) =
        groupHistory(times.toList(), now, zone) { it }

    @Test
    fun `empty input gives no groups`() {
        assertTrue(group().isEmpty())
    }

    @Test
    fun `entries land in the right buckets`() {
        val today = at(2026, Calendar.NOVEMBER, 8, 9)
        val yesterday = at(2026, Calendar.NOVEMBER, 7, 23)
        val threeDays = at(2026, Calendar.NOVEMBER, 5)
        val older = at(2026, Calendar.OCTOBER, 1)

        val result = group(today, yesterday, threeDays, older).toMap()

        assertEquals(listOf(today), result[HistoryGroup.TODAY])
        assertEquals(listOf(yesterday), result[HistoryGroup.YESTERDAY])
        assertEquals(listOf(threeDays), result[HistoryGroup.LAST_7_DAYS])
        assertEquals(listOf(older), result[HistoryGroup.OLDER])
    }

    @Test
    fun `boundaries are midnight based`() {
        val todayMidnight = at(2026, Calendar.NOVEMBER, 8, 0)
        val justBefore = todayMidnight - 1
        val result = group(todayMidnight, justBefore).toMap()
        assertEquals(listOf(todayMidnight), result[HistoryGroup.TODAY])
        assertEquals(listOf(justBefore), result[HistoryGroup.YESTERDAY])
    }

    @Test
    fun `last 7 days starts seven days before today and older follows`() {
        val edgeIn = at(2026, Calendar.NOVEMBER, 1, 0)      // exactly 7 days before today 00:00
        val edgeOut = edgeIn - 1
        val result = group(edgeIn, edgeOut).toMap()
        assertEquals(listOf(edgeIn), result[HistoryGroup.LAST_7_DAYS])
        assertEquals(listOf(edgeOut), result[HistoryGroup.OLDER])
    }

    @Test
    fun `groups come out in fixed order and empty groups are dropped`() {
        val older = at(2026, Calendar.JANUARY, 1)
        val today = at(2026, Calendar.NOVEMBER, 8, 1)
        val result = group(older, today)
        assertEquals(listOf(HistoryGroup.TODAY, HistoryGroup.OLDER), result.map { it.first })
    }

    @Test
    fun `order inside a group is preserved`() {
        val a = at(2026, Calendar.NOVEMBER, 8, 14)
        val b = at(2026, Calendar.NOVEMBER, 8, 10)
        val c = at(2026, Calendar.NOVEMBER, 8, 2)
        assertEquals(listOf(a, b, c), group(a, b, c).single().second)
    }

    @Test
    fun `future timestamps count as today`() {
        val future = now + 3 * 86_400_000L
        assertEquals(HistoryGroup.TODAY, group(future).single().first)
    }

    @Test
    fun `daylight saving change does not shift yesterday`() {
        val ny = TimeZone.getTimeZone("America/New_York")
        // 2026-11-01 is the DST end (25h day). now = Nov 2 10:00 local.
        fun local(day: Int, hour: Int) = Calendar.getInstance(ny).apply {
            clear(); set(2026, Calendar.NOVEMBER, day, hour, 0, 0)
        }.timeInMillis
        val nowNy = local(2, 10)
        val lateYesterday = local(1, 23)          // Nov 1, 23:00 local
        val result = groupHistory(listOf(lateYesterday), nowNy, ny) { it }
        assertEquals(HistoryGroup.YESTERDAY, result.single().first)
    }
}
