package com.auraplayer.app.data.model

data class Track(
    val id: Long,
    val mediaStoreId: Long?,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val format: AudioFormat,
    val mimeType: String,
    val sizeBytes: Long,
    val dateAddedSec: Long,
    val albumArtUri: String?,
    val isExternalImport: Boolean,
    val isAvailable: Boolean = true,
    val fingerprint: String = TrackFingerprint.compute(title, durationMs, sizeBytes)
)
