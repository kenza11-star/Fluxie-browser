package com.fluxie.browser.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedDomainDao {
    @Query("SELECT * FROM blocked_domains ORDER BY domain ASC")
    fun observeAll(): Flow<List<BlockedDomain>>

    @Query("SELECT * FROM blocked_domains WHERE category = :category ORDER BY domain ASC")
    fun observeByCategory(category: String): Flow<List<BlockedDomain>>

    @Query("SELECT * FROM blocked_domains")
    suspend fun getAll(): List<BlockedDomain>

    /** Returns the row id per item, or -1 for items skipped because the domain already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<BlockedDomain>): List<Long>

    @Query("DELETE FROM blocked_domains WHERE domain = :domain")
    suspend fun delete(domain: String): Int

    @Query("DELETE FROM blocked_domains WHERE category = :category")
    suspend fun deleteCategory(category: String): Int
}
