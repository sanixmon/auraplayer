package com.auraplayer.app.data.model

data class WatchedFolder(
    val uri: String,
    val displayName: String,
    val addedAtMs: Long,
    val lastScannedAtMs: Long
)
