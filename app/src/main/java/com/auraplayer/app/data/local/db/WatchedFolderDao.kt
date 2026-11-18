package com.auraplayer.app.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchedFolderDao {

    @Query("SELECT * FROM watched_folders ORDER BY addedAtMs DESC")
    fun observeAll(): Flow<List<WatchedFolderEntity>>

    @Query("SELECT * FROM watched_folders")
    suspend fun getAll(): List<WatchedFolderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(folder: WatchedFolderEntity)

    @Query("UPDATE watched_folders SET lastScannedAtMs = :scannedAtMs WHERE uri = :uri")
    suspend fun updateLastScanned(uri: String, scannedAtMs: Long)

    @Query("DELETE FROM watched_folders WHERE uri = :uri")
    suspend fun deleteByUri(uri: String)
}
