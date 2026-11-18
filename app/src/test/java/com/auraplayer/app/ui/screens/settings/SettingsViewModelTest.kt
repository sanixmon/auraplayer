package com.auraplayer.app.ui.screens.settings

import com.auraplayer.app.data.backup.AuraBackupManager
import com.auraplayer.app.data.local.prefs.VisualizerPreferencesDataSource
import com.auraplayer.app.data.model.VisualizerColorScheme
import com.auraplayer.app.data.model.VisualizerConfig
import com.auraplayer.app.data.model.VisualizerDensity
import com.auraplayer.app.data.model.VisualizerStyle
import com.auraplayer.app.playback.PlaybackController
import com.auraplayer.app.playback.PlaybackUiState
import com.auraplayer.app.playback.VisualizerBus
import com.auraplayer.app.util.MainDispatcherRule
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val preferences: VisualizerPreferencesDataSource = mockk(relaxed = true)
    private val backupManager: AuraBackupManager = mockk(relaxed = true)
    private val visualizerBus = VisualizerBus()
    private val playbackController: PlaybackController = mockk()

    @Test
    fun `customizing visualizer settings delegates to DataStore preferences`() = runTest {
        every { preferences.configFlow } returns flowOf(VisualizerConfig())
        every { playbackController.state } returns MutableStateFlow(PlaybackUiState())

        val viewModel = SettingsViewModel(
            preferences = preferences,
            backupManager = backupManager,
            visualizerBus = visualizerBus,
            playbackController = playbackController
        )

        viewModel.setStyle(VisualizerStyle.CIRCULAR_AURA)
        viewModel.setColorScheme(VisualizerColorScheme.NEON_CYBER)
        viewModel.setDensity(VisualizerDensity.ULTRA)
        viewModel.setSensitivity(1.8f)
        viewModel.setSmoothing(0.45f)
        viewModel.setGlowEnabled(false)
        viewModel.setMirrorEnabled(true)
        viewModel.setPeakCapsEnabled(false)
        viewModel.resetDefaults()

        coVerify { preferences.updateStyle(VisualizerStyle.CIRCULAR_AURA) }
        coVerify { preferences.updateColorScheme(VisualizerColorScheme.NEON_CYBER) }
        coVerify { preferences.updateDensity(VisualizerDensity.ULTRA) }
        coVerify { preferences.updateSensitivity(1.8f) }
        coVerify { preferences.updateSmoothing(0.45f) }
        coVerify { preferences.updateGlowEnabled(false) }
        coVerify { preferences.updateMirrorEnabled(true) }
        coVerify { preferences.updatePeakCapsEnabled(false) }
        coVerify { preferences.resetToDefaults() }
    }
}
