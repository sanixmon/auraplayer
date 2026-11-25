package com.auraplayer.app.data.backup

import android.content.Context
import com.auraplayer.app.data.local.db.PlaylistDao
import com.auraplayer.app.data.local.db.TrackDao
import com.auraplayer.app.data.local.db.TrackEntity
import com.auraplayer.app.data.local.prefs.VisualizerPreferencesDataSource
import com.auraplayer.app.data.model.AudioFormat
import com.auraplayer.app.data.model.EmberDirection
import com.auraplayer.app.data.model.TrackFingerprint
import com.auraplayer.app.data.model.VisualizerColorScheme
import com.auraplayer.app.data.model.VisualizerConfig
import com.auraplayer.app.data.model.VisualizerDensity
import com.auraplayer.app.data.model.VisualizerStyle
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuraBackupManagerTest {

    private val context: Context = mockk(relaxed = true)
    private val trackDao: TrackDao = mockk(relaxed = true)
    private val playlistDao: PlaylistDao = mockk(relaxed = true)
    private val visualizerPrefs: VisualizerPreferencesDataSource = mockk(relaxed = true)

    private val backupManager = AuraBackupManager(
        context = context,
        trackDao = trackDao,
        playlistDao = playlistDao,
        visualizerPreferences = visualizerPrefs
    )

    @Test
    fun `importFromJson links matched tracks, inserts unavailable placeholders for missing ones, and restores visualizer config`() = runTest {
        val existingFp = TrackFingerprint.compute("Existing Song", 200_000L, 12_000_000L)
        val missingFp = TrackFingerprint.compute("Missing Song", 150_000L, 9_000_000L)

        val existingTrack = TrackEntity(
            id = 42L,
            mediaStoreId = 42L,
            uri = "content://media/42",
            title = "Existing Song",
            artist = "Artist A",
            album = "Album A",
            durationMs = 200_000L,
            format = AudioFormat.FLAC.name,
            mimeType = "audio/flac",
            sizeBytes = 12_000_000L,
            dateAddedSec = 100L,
            albumArtUri = null,
            isExternalImport = false,
            isAvailable = true,
            fingerprint = existingFp
        )

        coEvery { playlistDao.insertPlaylist(any()) } returns 10L
        coEvery { trackDao.getTrackByFingerprint(existingFp) } returns existingTrack
        coEvery { trackDao.getTrackByFingerprint(missingFp) } returns null
        coEvery { trackDao.insertSingleIgnore(any()) } returns 99L

        val json = """
            {
              "schemaVersion": 1,
              "exportedAtMs": 123456789,
              "playlists": [
                {
                  "name": "Migrated Mix",
                  "description": "From old phone",
                  "entries": [
                    {
                      "title": "Existing Song",
                      "artist": "Artist A",
                      "album": "Album A",
                      "durationMs": 200000,
                      "sizeBytes": 12000000,
                      "format": "FLAC",
                      "fingerprint": "$existingFp"
                    },
                    {
                      "title": "Missing Song",
                      "artist": "Artist B",
                      "album": "Album B",
                      "durationMs": 150000,
                      "sizeBytes": 9000000,
                      "format": "ALAC",
                      "fingerprint": "$missingFp"
                    }
                  ]
                }
              ],
              "visualizerConfig": {
                "style": "CIRCULAR_AURA",
                "colorScheme": "SOLAR_FLARE",
                "density": "HIGH",
                "sensitivity": 1.6,
                "smoothing": 0.5,
                "glowEnabled": true,
                "mirrorEnabled": true,
                "peakCapsEnabled": false
              }
            }
        """.trimIndent()

        val result = backupManager.importFromJson(json)

        assertEquals(1, result.playlistsImported)
        assertEquals(1, result.tracksLinked)
        assertEquals(1, result.tracksPlaceholderUnavailable)
        assertTrue(result.visualizerRestored)

        coVerify {
            trackDao.insertSingleIgnore(match { !it.isAvailable && it.fingerprint == missingFp })
        }
        coVerify { visualizerPrefs.updateStyle(VisualizerStyle.CIRCULAR_AURA) }
        coVerify { visualizerPrefs.updateColorScheme(VisualizerColorScheme.SOLAR_FLARE) }
        coVerify { visualizerPrefs.updateDensity(VisualizerDensity.HIGH) }
    }

    @Test
    fun `exportFullBackupJson and importFromJson round-trip Fire Ember direction and custom colors`() = runTest {
        coEvery { playlistDao.getAllPlaylistsSnapshot() } returns emptyList()
        every { visualizerPrefs.configFlow } returns flowOf(
            VisualizerConfig(
                style = VisualizerStyle.FIRE_EMBER,
                emberDirection = EmberDirection.LEFT,
                colorScheme = VisualizerColorScheme.CUSTOM,
                density = VisualizerDensity.ULTRA,
                beatPunch = 1.6f,
                beatSensitivity = 1.35f,
                bassBoost = 1.4f,
                syncOffsetMs = -20,
                customVizPrimaryArgb = 0xFFFFAA00,
                customVizSecondaryArgb = 0xFFCC2200
            )
        )

        val exportedJson = backupManager.exportFullBackupJson()
        val result = backupManager.importFromJson(exportedJson)

        assertTrue(result.visualizerRestored)
        coVerify { visualizerPrefs.updateStyle(VisualizerStyle.FIRE_EMBER) }
        coVerify { visualizerPrefs.updateEmberDirection(EmberDirection.LEFT) }
        coVerify { visualizerPrefs.updateCustomVisualizerColors(0xFFFFAA00, 0xFFCC2200) }
        coVerify { visualizerPrefs.updateColorScheme(VisualizerColorScheme.CUSTOM) }
        coVerify { visualizerPrefs.updateDensity(VisualizerDensity.ULTRA) }
        coVerify { visualizerPrefs.updateBeatPunch(1.6f) }
        coVerify { visualizerPrefs.updateBeatSensitivity(1.35f) }
        coVerify { visualizerPrefs.updateBassBoost(1.4f) }
        coVerify { visualizerPrefs.updateSyncOffsetMs(-20) }
    }
}
