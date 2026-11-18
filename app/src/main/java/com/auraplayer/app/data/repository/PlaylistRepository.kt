package com.auraplayer.app.data.repository

import com.auraplayer.app.data.local.db.PlaylistDao
import com.auraplayer.app.data.local.db.PlaylistEntity
import com.auraplayer.app.data.model.Playlist
import com.auraplayer.app.data.model.PlaylistEntry
import com.auraplayer.app.data.model.PlaylistWithEntries
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class PlaylistRepository(
    private val playlistDao: PlaylistDao,
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) {

    fun observePlaylists(): Flow<List<Playlist>> =
        playlistDao.observePlaylistsWithCount().map { rows ->
            rows.map { row ->
                Playlist(
                    id = row.id,
                    name = row.name,
                    description = row.description,
                    trackCount = row.trackCount,
                    createdAtMs = row.createdAtMs,
                    updatedAtMs = row.updatedAtMs
                )
            }
        }

    fun observePlaylistWithEntries(playlistId: Long): Flow<PlaylistWithEntries?> =
        combine(
            playlistDao.observePlaylist(playlistId),
            playlistDao.observeEntriesWithTrack(playlistId)
        ) { entity, relations ->
            entity?.let {
                PlaylistWithEntries(
                    playlist = Playlist(
                        id = it.id,
                        name = it.name,
                        description = it.description,
                        trackCount = relations.size,
                        createdAtMs = it.createdAtMs,
                        updatedAtMs = it.updatedAtMs
                    ),
                    entries = relations.map { rel ->
                        PlaylistEntry(
                            entryId = rel.entry.entryId,
                            playlistId = rel.entry.playlistId,
                            position = rel.entry.position,
                            track = rel.track.toDomain()
                        )
                    }
                )
            }
        }

    suspend fun createPlaylist(name: String, description: String = ""): Long {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Playlist name cannot be blank" }
        val now = clockMs()
        return playlistDao.insertPlaylist(
            PlaylistEntity(
                name = trimmed,
                description = description.trim(),
                createdAtMs = now,
                updatedAtMs = now
            )
        )
    }

    suspend fun updatePlaylist(playlistId: Long, name: String, description: String) {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Playlist name cannot be blank" }
        playlistDao.updatePlaylistDetails(
            playlistId = playlistId,
            name = trimmed,
            description = description.trim(),
            updatedAtMs = clockMs()
        )
    }

    suspend fun deletePlaylist(playlistId: Long) {
        playlistDao.deletePlaylist(playlistId)
    }

    suspend fun addTrackToPlaylist(playlistId: Long, trackId: Long) {
        playlistDao.addTrackAtEnd(playlistId, trackId, clockMs())
    }

    suspend fun removeEntry(playlistId: Long, entryId: Long) {
        playlistDao.removeEntryById(entryId)
        playlistDao.touchPlaylist(playlistId, clockMs())
    }

    suspend fun reorderEntries(playlistId: Long, orderedEntryIds: List<Long>) {
        playlistDao.reorderEntries(
            playlistId = playlistId,
            orderedEntryIds = orderedEntryIds,
            nowMs = clockMs()
        )
    }
}
