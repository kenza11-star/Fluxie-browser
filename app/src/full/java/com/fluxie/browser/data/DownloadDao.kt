package com.fluxie.browser.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<DownloadEntry>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getById(id: Long): DownloadEntry?

    @Insert
    suspend fun insert(entry: DownloadEntry): Long

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: Long)
}
