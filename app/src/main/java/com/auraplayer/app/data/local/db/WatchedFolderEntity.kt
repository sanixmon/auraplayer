package com.auraplayer.app.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.auraplayer.app.data.model.WatchedFolder

@Entity(tableName = "watched_folders")
data class WatchedFolderEntity(
    @PrimaryKey val uri: String,
    val displayName: String,
    val addedAtMs: Long,
    val lastScannedAtMs: Long
) {
    fun toDomain(): WatchedFolder = WatchedFolder(
        uri = uri,
        displayName = displayName,
        addedAtMs = addedAtMs,
        lastScannedAtMs = lastScannedAtMs
    )
}
