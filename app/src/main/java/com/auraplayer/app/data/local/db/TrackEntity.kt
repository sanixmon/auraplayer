package com.auraplayer.app.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.auraplayer.app.data.model.AudioFormat
import com.auraplayer.app.data.model.Track
import com.auraplayer.app.data.model.TrackFingerprint

@Entity(
    tableName = "tracks",
    indices = [
        Index(value = ["uri"], unique = true),
        Index(value = ["fingerprint"]),
        Index(value = ["title"]),
        Index(value = ["artist"]),
        Index(value = ["format"]),
        Index(value = ["isAvailable"])
    ]
)
data class TrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val mediaStoreId: Long?,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val format: String,
    val mimeType: String,
    val sizeBytes: Long,
    val dateAddedSec: Long,
    val albumArtUri: String?,
    val isExternalImport: Boolean,
    val isAvailable: Boolean = true,
    val fingerprint: String = TrackFingerprint.compute(title, durationMs, sizeBytes)
) {
    fun toDomain(): Track = Track(
        id = id,
        mediaStoreId = mediaStoreId,
        uri = uri,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        format = AudioFormat.entries.firstOrNull { it.name == format }
            ?: AudioFormat.fromMetadata(mimeType, uri),
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        dateAddedSec = dateAddedSec,
        albumArtUri = albumArtUri,
        isExternalImport = isExternalImport,
        isAvailable = isAvailable,
        fingerprint = fingerprint
    )
}
