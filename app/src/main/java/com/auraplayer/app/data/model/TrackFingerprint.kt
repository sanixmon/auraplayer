package com.auraplayer.app.data.model

/**
 * Canonical deduplication and cross-device relinking key for local Tracks.
 * Normalizes title/filename + rounded duration (seconds) + byte size so MediaStore URIs,
 * SAF Watched Folder URIs, and restored Aura Backups match consistently.
 */
object TrackFingerprint {
    fun compute(
        titleOrFileName: String,
        durationMs: Long,
        sizeBytes: Long
    ): String {
        val normalizedName = titleOrFileName
            .substringBeforeLast('.')
            .lowercase()
            .replace(Regex("[^a-z0-9]"), "")
            .ifEmpty { "track" }
        val durationSec = (durationMs.coerceAtLeast(0L) / 1000L)
        val safeSize = sizeBytes.coerceAtLeast(0L)
        return "${normalizedName}_${durationSec}_${safeSize}"
    }
}
