package com.auraplayer.app.data.repository

import com.auraplayer.app.data.local.db.PlaylistDao
import com.auraplayer.app.data.local.db.PlaylistEntity
import com.auraplayer.app.data.local.db.PlaylistEntryEntity
import com.auraplayer.app.data.local.db.PlaylistEntryWithTrackRelation
import com.auraplayer.app.data.local.db.TrackEntity
import com.auraplayer.app.data.model.AudioFormat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PlaylistRepositoryTest {

    private val playlistDao: PlaylistDao = mockk(relaxed = true)
    private val repository = PlaylistRepository(playlistDao, clockMs = { 42_000L })

    @Test(expected = IllegalArgumentException::class)
    fun `createPlaylist rejects blank names`() = runTest {
        repository.createPlaylist("   ", "desc")
    }

    @Test
    fun `createPlaylist trims fields and stamps clock`() = runTest {
        coEvery { playlistDao.insertPlaylist(any()) } returns 7L

        val id = repository.createPlaylist(" Focus Mix ", " Deep work ")

        assertEquals(7L, id)
        coVerify {
            playlistDao.insertPlaylist(
                PlaylistEntity(
                    name = "Focus Mix",
                    description = "Deep work",
                    createdAtMs = 42_000L,
                    updatedAtMs = 42_000L
                )
            )
        }
    }

    @Test
    fun `reorderEntries persists surrogate entryIds in order`() = runTest {
        val newOrder = listOf(103L, 101L, 102L)
        repository.reorderEntries(playlistId = 5L, orderedEntryIds = newOrder)

        coVerify(exactly = 1) {
            playlistDao.reorderEntries(5L, newOrder, 42_000L)
        }
    }

    @Test
    fun `observePlaylistWithEntries supports duplicate track entries with distinct entryIds`() = runTest {
        val entity = PlaylistEntity(
            id = 5L,
            name = "Night Drive",
            description = "Synthwave",
            createdAtMs = 100L,
            updatedAtMs = 200L
        )
        val track = TrackEntity(
            id = 11L,
            mediaStoreId = null,
            uri = "content://doc/drive.mp3",
            title = "Neon Highway",
            artist = "Vesper",
            album = "Afterhours",
            durationMs = 210_000L,
            format = AudioFormat.MP3.name,
            mimeType = "audio/mpeg",
            sizeBytes = 5_000_000L,
            dateAddedSec = 1_700_000_000L,
            albumArtUri = null,
            isExternalImport = true,
            isAvailable = true
        )

        val rel1 = PlaylistEntryWithTrackRelation(
            entry = PlaylistEntryEntity(entryId = 1L, playlistId = 5L, trackId = 11L, position = 0),
            track = track
        )
        val rel2 = PlaylistEntryWithTrackRelation(
            entry = PlaylistEntryEntity(entryId = 2L, playlistId = 5L, trackId = 11L, position = 1),
            track = track
        )

        coEvery { playlistDao.observePlaylist(5L) } returns flowOf(entity)
        coEvery { playlistDao.observeEntriesWithTrack(5L) } returns flowOf(listOf(rel1, rel2))

        val result = repository.observePlaylistWithEntries(5L).first()

        assertNotNull(result)
        assertEquals("Night Drive", result?.playlist?.name)
        assertEquals(2, result?.playlist?.trackCount)
        assertEquals(listOf(1L, 2L), result?.entries?.map { it.entryId })
    }
}
