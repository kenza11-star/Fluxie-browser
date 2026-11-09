package com.fluxie.browser.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Which user-managed list a blocked domain belongs to. */
enum class BlockCategory {
    /** Always blocked. */
    GENERAL,

    /** Blocked only while the adult-content filter is switched on. */
    ADULT
}

/** One domain on a user-managed list. [domain] is normalized (see DomainRules) and unique across lists. */
@Entity(tableName = "blocked_domains", indices = [Index(value = ["category"])])
data class BlockedDomain(
    @PrimaryKey val domain: String,
    val category: String,
    val addedAt: Long
)
