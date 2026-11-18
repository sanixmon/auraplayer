package com.auraplayer.app.ui.screens.library

import androidx.paging.PagingData
import app.cash.turbine.test
import com.auraplayer.app.data.model.AudioFormat
import com.auraplayer.app.data.repository.LibrarySortOrder
import com.auraplayer.app.data.repository.MusicRepository
import com.auraplayer.app.data.repository.PlaylistRepository
import com.auraplayer.app.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LibraryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val musicRepository: MusicRepository = mockk()
    private val playlistRepository: PlaylistRepository = mockk()

    private fun setupDefaults() {
        every {
            musicRepository.observePagedTracks(any(), any(), any(), any())
        } returns flowOf(PagingData.empty())
        every { musicRepository.observeWatchedFolders() } returns flowOf(emptyList())
        every { musicRepository.observeUnavailableCount() } returns flowOf(2)
        every { playlistRepository.observePlaylists() } returns flowOf(emptyList())
    }

    @Test
    fun `search query, format filter, unavailable toggle, and sort order update uiState`() = runTest {
        setupDefaults()

        val viewModel = LibraryViewModel(musicRepository, playlistRepository)

        viewModel.uiState.test {
            val initial = awaitItem()
            assertEquals(2, initial.unavailableCount)

            viewModel.onSearchQueryChange("Aurora")
            assertEquals("Aurora", awaitItem().searchQuery)

            viewModel.onFormatFilterSelected(AudioFormat.FLAC)
            assertEquals(AudioFormat.FLAC, awaitItem().selectedFormat)

            viewModel.onSortOrderSelected(LibrarySortOrder.DURATION)
            assertEquals(LibrarySortOrder.DURATION, awaitItem().sortOrder)

            viewModel.toggleShowUnavailable()
            assertTrue(awaitItem().showUnavailable)
        }
    }

    @Test
    fun `syncLibrary emits status message with track count`() = runTest {
        setupDefaults()
        coEvery { musicRepository.syncDeviceLibrary() } returns 14

        val viewModel = LibraryViewModel(musicRepository, playlistRepository)
        viewModel.uiState.test {
            awaitItem()
            viewModel.syncLibrary()
            val updated = expectMostRecentItem()
            assertEquals("Library scan complete: 14 available tracks", updated.statusMessage)
        }
    }
}
