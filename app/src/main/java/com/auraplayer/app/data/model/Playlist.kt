package com.auraplayer.app.data.model

data class Playlist(
    val id: Long,
    val name: String,
    val description: String,
    val trackCount: Int,
    val createdAtMs: Long,
    val updatedAtMs: Long
)

data class PlaylistEntry(
    val entryId: Long,
    val playlistId: Long,
    val position: Int,
    val track: Track
)

data class PlaylistWithEntries(
    val playlist: Playlist,
    val entries: List<PlaylistEntry>
)
