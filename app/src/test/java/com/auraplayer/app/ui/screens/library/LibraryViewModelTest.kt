package com.auraplayer.app.ui.screens.library

import androidx.paging.PagingData
import app.cash.turbine.test
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
import org.junit.Assert.assertNull
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
        every { playlistRepository.observePlaylists() } returns flowOf(emptyList())
    }

    @Test
    fun `search query and sort order update uiState`() = runTest {
        setupDefaults()

        val viewModel = LibraryViewModel(musicRepository, playlistRepository)

        viewModel.uiState.test {
            awaitItem()

            viewModel.onSearchQueryChange("Aurora")
            assertEquals("Aurora", awaitItem().searchQuery)

            viewModel.onSortOrderSelected(LibrarySortOrder.DURATION)
            assertEquals(LibrarySortOrder.DURATION, awaitItem().sortOrder)
        }
    }

    @Test
    fun `silent syncLibrary populates without snackbar while manual sync reports count`() = runTest {
        setupDefaults()
        coEvery { musicRepository.syncDeviceLibrary() } returns 14

        val viewModel = LibraryViewModel(musicRepository, playlistRepository)
        viewModel.uiState.test {
            awaitItem()
            viewModel.syncLibrary(silent = true)
            assertNull(expectMostRecentItem().statusMessage)

            viewModel.syncLibrary(silent = false)
            assertEquals("14 songs found", expectMostRecentItem().statusMessage)
        }
    }
}
