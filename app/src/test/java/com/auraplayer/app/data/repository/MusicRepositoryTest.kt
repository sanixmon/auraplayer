package com.auraplayer.app.data.repository

import com.auraplayer.app.data.local.db.TrackDao
import com.auraplayer.app.data.local.db.TrackEntity
import com.auraplayer.app.data.local.db.WatchedFolderDao
import com.auraplayer.app.data.local.db.WatchedFolderEntity
import com.auraplayer.app.data.model.AudioFormat
import com.auraplayer.app.data.model.TrackFingerprint
import com.auraplayer.app.data.scanner.LocalAudioScanner
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicRepositoryTest {

    private val trackDao: TrackDao = mockk(relaxed = true)
    private val watchedFolderDao: WatchedFolderDao = mockk(relaxed = true)
    private val scanner: LocalAudioScanner = mockk()
    private val repository = MusicRepository(
        trackDao = trackDao,
        watchedFolderDao = watchedFolderDao,
        scanner = scanner,
        clockMs = { 90_000L }
    )

    private fun sampleEntity(
        id: Long = 1L,
        uri: String = "content://media/$id",
        title: String = "Midnight Echo",
        isExternal: Boolean = false,
        isAvailable: Boolean = true
    ) = TrackEntity(
        id = id,
        mediaStoreId = if (isExternal) null else id,
        uri = uri,
        title = title,
        artist = "Aura",
        album = "Synth",
        durationMs = 180_000L,
        format = AudioFormat.FLAC.name,
        mimeType = "audio/flac",
        sizeBytes = 25_000_000L,
        dateAddedSec = 1_700_000_000L,
        albumArtUri = null,
        isExternalImport = isExternal,
        isAvailable = isAvailable,
        fingerprint = TrackFingerprint.compute(title, 180_000L, 25_000_000L)
    )

    @Test
    fun `syncDeviceLibrary deduplicates WatchedFolder tracks against MediaStore by fingerprint`() = runTest {
        val mediaStoreTrack = sampleEntity(id = 1L, uri = "content://media/1", title = "Midnight Echo")
        val duplicateInFolder = sampleEntity(
            id = 0L,
            uri = "content://tree/folder/midnight_echo.flac",
            title = "Midnight Echo",
            isExternal = true
        )
        val uniqueInFolder = sampleEntity(
            id = 0L,
            uri = "content://tree/folder/hidden_gem.ape",
            title = "Hidden Gem",
            isExternal = true
        )

        coEvery { scanner.scanDeviceMediaStore() } returns listOf(mediaStoreTrack)
        coEvery { watchedFolderDao.getAll() } returns listOf(
            WatchedFolderEntity("content://tree/folder", "SD Music", 10L, 0L)
        )
        coEvery { scanner.scanWatchedFolder("content://tree/folder") } returns listOf(
            duplicateInFolder,
            uniqueInFolder
        )
        coEvery { trackDao.getAllTracksSnapshot() } returns emptyList()

        val scannedSlot = slot<List<TrackEntity>>()
        coEvery { trackDao.syncScannedTracks(capture(scannedSlot), any()) } returns Unit

        val count = repository.syncDeviceLibrary()

        assertEquals(2, count)
        assertEquals(listOf("content://media/1", "content://tree/folder/hidden_gem.ape"), scannedSlot.captured.map { it.uri })
        coVerify { watchedFolderDao.updateLastScanned("content://tree/folder", 90_000L) }
    }

    @Test
    fun `importExternalFiles relinks unavailable track matching fingerprint`() = runTest {
        val newUri = "content://doc/relinked.opus"
        val extracted = sampleEntity(0L, newUri, "Restored Song", isExternal = true)
        val unavailableExisting = extracted.copy(
            id = 55L,
            uri = "unavailable://${extracted.fingerprint}/uuid",
            isAvailable = false
        )

        coEvery { scanner.extractExternalTrack(newUri) } returns extracted
        coEvery { trackDao.getTrackByUri(newUri) } returns null
        coEvery { trackDao.getTrackByFingerprint(extracted.fingerprint) } returns unavailableExisting

        val added = repository.importExternalFiles(listOf(newUri))

        assertEquals(1, added)
        coVerify(exactly = 1) {
            trackDao.updateAndRelinkTrack(
                id = 55L,
                mediaStoreId = null,
                uri = newUri,
                title = extracted.title,
                artist = extracted.artist,
                album = extracted.album,
                durationMs = extracted.durationMs,
                format = extracted.format,
                mimeType = extracted.mimeType,
                sizeBytes = extracted.sizeBytes,
                dateAddedSec = extracted.dateAddedSec,
                albumArtUri = null,
                isExternalImport = true,
                fingerprint = extracted.fingerprint
            )
        }
    }

    @Test
    fun `addWatchedFolder persists folder and triggers Library Scan`() = runTest {
        val treeUri = "content://com.android.externalstorage.documents/tree/primary%3AMusic"
        coEvery { scanner.takeFolderPermissionAndName(treeUri) } returns "Music"
        coEvery { scanner.scanDeviceMediaStore() } returns emptyList()
        coEvery { watchedFolderDao.getAll() } returns emptyList()
        coEvery { trackDao.getAllTracksSnapshot() } returns emptyList()

        val result = repository.addWatchedFolder(treeUri)

        assertTrue(result)
        coVerify {
            watchedFolderDao.upsert(
                WatchedFolderEntity(
                    uri = treeUri,
                    displayName = "Music",
                    addedAtMs = 90_000L,
                    lastScannedAtMs = 0L
                )
            )
        }
    }
}
