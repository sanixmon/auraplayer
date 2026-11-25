package com.auraplayer.app.data.backup

import android.content.Context
import android.net.Uri
import com.auraplayer.app.data.local.db.PlaylistDao
import com.auraplayer.app.data.local.db.PlaylistEntity
import com.auraplayer.app.data.local.db.PlaylistEntryEntity
import com.auraplayer.app.data.local.db.TrackDao
import com.auraplayer.app.data.local.db.TrackEntity
import com.auraplayer.app.data.local.prefs.VisualizerPreferencesDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class AuraBackupManager(
    private val context: Context,
    private val trackDao: TrackDao,
    private val playlistDao: PlaylistDao,
    private val visualizerPreferences: VisualizerPreferencesDataSource
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    suspend fun exportPlaylistJson(playlistId: Long): String = withContext(Dispatchers.IO) {
        val playlists = playlistDao.getAllPlaylistsSnapshot()
        val target = playlists.firstOrNull { it.id == playlistId }
            ?: error("Playlist not found")
        val backupPlaylist = buildBackupPlaylist(target)
        val doc = AuraBackupDocument(
            schemaVersion = 1,
            exportedAtMs = System.currentTimeMillis(),
            playlists = listOf(backupPlaylist),
            visualizerConfig = null
        )
        json.encodeToString(doc)
    }

    suspend fun exportFullBackupJson(): String = withContext(Dispatchers.IO) {
        val playlists = playlistDao.getAllPlaylistsSnapshot().map { buildBackupPlaylist(it) }
        val config = visualizerPreferences.configFlow.first()
        val doc = AuraBackupDocument(
            schemaVersion = 1,
            exportedAtMs = System.currentTimeMillis(),
            playlists = playlists,
            visualizerConfig = BackupVisualizerConfig.fromDomain(config)
        )
        json.encodeToString(doc)
    }

    suspend fun writeBackupToUri(uriString: String, content: String): Boolean =
        withContext(Dispatchers.IO) {
            val uri = Uri.parse(uriString)
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(content.toByteArray(Charsets.UTF_8))
                out.flush()
                true
            } ?: false
        }

    suspend fun importFromUri(uriString: String): BackupImportResult = withContext(Dispatchers.IO) {
        val uri = Uri.parse(uriString)
        val raw = context.contentResolver.openInputStream(uri)?.use { input ->
            input.bufferedReader(Charsets.UTF_8).readText()
        } ?: error("Unable to read backup file")
        importFromJson(raw)
    }

    suspend fun importFromJson(rawJson: String): BackupImportResult {
        val doc = json.decodeFromString<AuraBackupDocument>(rawJson)
        var linkedCount = 0
        var placeholderCount = 0
        val now = System.currentTimeMillis()

        for (backupPlaylist in doc.playlists) {
            val safeName = backupPlaylist.name.trim().ifEmpty { "Imported Playlist" }
            val newPlaylistId = playlistDao.insertPlaylist(
                PlaylistEntity(
                    name = safeName,
                    description = backupPlaylist.description.trim(),
                    createdAtMs = now,
                    updatedAtMs = now
                )
            )

            val entriesToInsert = mutableListOf<PlaylistEntryEntity>()
            backupPlaylist.entries.forEachIndexed { index, ref ->
                val matched = trackDao.getTrackByFingerprint(ref.fingerprint)
                val trackId = if (matched != null) {
                    if (matched.isAvailable) linkedCount++ else placeholderCount++
                    matched.id
                } else {
                    placeholderCount++
                    val placeholder = TrackEntity(
                        mediaStoreId = null,
                        uri = "unavailable://${ref.fingerprint}/${UUID.randomUUID()}",
                        title = ref.title.ifBlank { "Missing Track" },
                        artist = ref.artist.ifBlank { "Unknown Artist" },
                        album = ref.album.ifBlank { "Unknown Album" },
                        durationMs = ref.durationMs.coerceAtLeast(0L),
                        format = ref.format,
                        mimeType = "audio/*",
                        sizeBytes = ref.sizeBytes.coerceAtLeast(0L),
                        dateAddedSec = now / 1000L,
                        albumArtUri = null,
                        isExternalImport = true,
                        isAvailable = false,
                        fingerprint = ref.fingerprint
                    )
                    trackDao.insertSingleIgnore(placeholder)
                }

                if (trackId > 0L) {
                    entriesToInsert += PlaylistEntryEntity(
                        playlistId = newPlaylistId,
                        trackId = trackId,
                        position = index
                    )
                }
            }

            if (entriesToInsert.isNotEmpty()) {
                playlistDao.insertEntries(entriesToInsert)
            }
        }

        var visualizerRestored = false
        doc.visualizerConfig?.let { savedConfig ->
            val domain = savedConfig.toDomain()
            visualizerPreferences.updateStyle(domain.style)
            if (savedConfig.emberDirection != null) {
                visualizerPreferences.updateEmberDirection(domain.emberDirection)
            }
            if (savedConfig.customVizPrimaryArgb != null && savedConfig.customVizSecondaryArgb != null) {
                visualizerPreferences.updateCustomVisualizerColors(
                    domain.customVizPrimaryArgb,
                    domain.customVizSecondaryArgb
                )
            }
            visualizerPreferences.updateColorScheme(domain.colorScheme)
            visualizerPreferences.updateDensity(domain.density)
            visualizerPreferences.updateSensitivity(domain.sensitivity)
            visualizerPreferences.updateSmoothing(domain.smoothing)
            if (savedConfig.beatPunch != null) {
                visualizerPreferences.updateBeatPunch(domain.beatPunch)
            }
            if (savedConfig.beatSensitivity != null) {
                visualizerPreferences.updateBeatSensitivity(domain.beatSensitivity)
            }
            if (savedConfig.bassBoost != null) {
                visualizerPreferences.updateBassBoost(domain.bassBoost)
            }
            if (savedConfig.syncOffsetMs != null) {
                visualizerPreferences.updateSyncOffsetMs(domain.syncOffsetMs)
            }
            if (savedConfig.freqMinHz != null || savedConfig.freqMaxHz != null) {
                visualizerPreferences.updateFreqRange(domain.freqMinHz, domain.freqMaxHz)
            }
            if (savedConfig.backgroundMode != null) {
                visualizerPreferences.updateBackgroundMode(domain.backgroundMode)
            }
            if (savedConfig.particleOverlay != null) {
                visualizerPreferences.updateParticleOverlay(domain.particleOverlay)
            }
            if (savedConfig.cameraShakeIntensity != null) {
                visualizerPreferences.updateCameraShakeIntensity(domain.cameraShakeIntensity)
            }
            if (savedConfig.rgbSplitIntensity != null) {
                visualizerPreferences.updateRgbSplitIntensity(domain.rgbSplitIntensity)
            }
            if (savedConfig.additiveBlendEnabled != null) {
                visualizerPreferences.updateAdditiveBlendEnabled(domain.additiveBlendEnabled)
            }
            if (savedConfig.vignetteEnabled != null) {
                visualizerPreferences.updateVignetteEnabled(domain.vignetteEnabled)
            }
            if (savedConfig.sharpBarsEnabled != null) {
                visualizerPreferences.updateSharpBarsEnabled(domain.sharpBarsEnabled)
            }
            visualizerPreferences.updateGlowEnabled(domain.glowEnabled)
            visualizerPreferences.updateMirrorEnabled(domain.mirrorEnabled)
            visualizerPreferences.updatePeakCapsEnabled(domain.peakCapsEnabled)
            visualizerRestored = true
        }

        return BackupImportResult(
            playlistsImported = doc.playlists.size,
            tracksLinked = linkedCount,
            tracksPlaceholderUnavailable = placeholderCount,
            visualizerRestored = visualizerRestored
        )
    }

    private suspend fun buildBackupPlaylist(playlist: PlaylistEntity): BackupPlaylist {
        val entries = playlistDao.getEntriesWithTrackSnapshot(playlist.id)
        return BackupPlaylist(
            name = playlist.name,
            description = playlist.description,
            entries = entries.map { rel ->
                val t = rel.track
                BackupTrackRef(
                    title = t.title,
                    artist = t.artist,
                    album = t.album,
                    durationMs = t.durationMs,
                    sizeBytes = t.sizeBytes,
                    format = t.format,
                    fingerprint = t.fingerprint
                )
            }
        )
    }
}
