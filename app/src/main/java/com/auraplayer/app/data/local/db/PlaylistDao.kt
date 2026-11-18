package com.auraplayer.app.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

data class PlaylistWithCountRow(
    val id: Long,
    val name: String,
    val description: String,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val trackCount: Int
)

@Dao
interface PlaylistDao {

    @Query(
        """
        SELECT p.id, p.name, p.description, p.createdAtMs, p.updatedAtMs,
               COUNT(pe.entryId) AS trackCount
        FROM playlists p
        LEFT JOIN playlist_entries pe ON p.id = pe.playlistId
        GROUP BY p.id
        ORDER BY p.updatedAtMs DESC
        """
    )
    fun observePlaylistsWithCount(): Flow<List<PlaylistWithCountRow>>

    @Query("SELECT * FROM playlists ORDER BY updatedAtMs DESC")
    suspend fun getAllPlaylistsSnapshot(): List<PlaylistEntity>

    @Query("SELECT * FROM playlists WHERE id = :playlistId LIMIT 1")
    fun observePlaylist(playlistId: Long): Flow<PlaylistEntity?>

    @Transaction
    @Query("SELECT * FROM playlist_entries WHERE playlistId = :playlistId ORDER BY position ASC")
    fun observeEntriesWithTrack(playlistId: Long): Flow<List<PlaylistEntryWithTrackRelation>>

    @Transaction
    @Query("SELECT * FROM playlist_entries WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun getEntriesWithTrackSnapshot(playlistId: Long): List<PlaylistEntryWithTrackRelation>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name, description = :description, updatedAtMs = :updatedAtMs WHERE id = :playlistId")
    suspend fun updatePlaylistDetails(
        playlistId: Long,
        name: String,
        description: String,
        updatedAtMs: Long
    )

    @Query("UPDATE playlists SET updatedAtMs = :updatedAtMs WHERE id = :playlistId")
    suspend fun touchPlaylist(playlistId: Long, updatedAtMs: Long)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_entries WHERE playlistId = :playlistId")
    suspend fun getMaxPosition(playlistId: Long): Int

    @Insert
    suspend fun insertEntry(entry: PlaylistEntryEntity): Long

    @Insert
    suspend fun insertEntries(entries: List<PlaylistEntryEntity>)

    @Query("DELETE FROM playlist_entries WHERE entryId = :entryId")
    suspend fun removeEntryById(entryId: Long)

    @Query("UPDATE playlist_entries SET position = :position WHERE entryId = :entryId AND playlistId = :playlistId")
    suspend fun updateEntryPosition(playlistId: Long, entryId: Long, position: Int)

    @Transaction
    suspend fun addTrackAtEnd(playlistId: Long, trackId: Long, nowMs: Long) {
        val nextPos = getMaxPosition(playlistId) + 1
        insertEntry(
            PlaylistEntryEntity(
                playlistId = playlistId,
                trackId = trackId,
                position = nextPos
            )
        )
        touchPlaylist(playlistId, nowMs)
    }

    @Transaction
    suspend fun reorderEntries(
        playlistId: Long,
        orderedEntryIds: List<Long>,
        nowMs: Long
    ) {
        orderedEntryIds.forEachIndexed { index, entryId ->
            updateEntryPosition(
                playlistId = playlistId,
                entryId = entryId,
                position = index
            )
        }
        touchPlaylist(playlistId, nowMs)
    }
}
