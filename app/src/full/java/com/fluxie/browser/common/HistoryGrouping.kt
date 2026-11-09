package com.fluxie.browser.common

import java.util.Calendar
import java.util.TimeZone

enum class HistoryGroup { TODAY, YESTERDAY, LAST_7_DAYS, OLDER }

/**
 * Splits [items] into Today / Yesterday / Last 7 days / Older, in that order, dropping empty
 * groups. Item order inside a group is preserved. Day boundaries come from the calendar (not
 * 24h arithmetic) so they stay right across daylight-saving changes. "Last 7 days" covers the
 * days from 2 up to 7 days before today; timestamps in the future count as today.
 */
fun <T> groupHistory(
    items: List<T>,
    now: Long = System.currentTimeMillis(),
    zone: TimeZone = TimeZone.getDefault(),
    timestampOf: (T) -> Long
): List<Pair<HistoryGroup, List<T>>> {
    if (items.isEmpty()) return emptyList()

    fun startOfDay(daysAgo: Int): Long = Calendar.getInstance(zone).apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_YEAR, -daysAgo)
    }.timeInMillis

    val todayStart = startOfDay(0)
    val yesterdayStart = startOfDay(1)
    val weekStart = startOfDay(7)

    val grouped = items.groupBy { item ->
        val t = timestampOf(item)
        when {
            t >= todayStart -> HistoryGroup.TODAY
            t >= yesterdayStart -> HistoryGroup.YESTERDAY
            t >= weekStart -> HistoryGroup.LAST_7_DAYS
            else -> HistoryGroup.OLDER
        }
    }
    return HistoryGroup.entries.mapNotNull { group -> grouped[group]?.let { group to it } }
}
