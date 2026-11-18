package com.auraplayer.app.ui.screens.settings

import com.auraplayer.app.data.local.prefs.VisualizerPreferencesDataSource
import com.auraplayer.app.data.model.AppAccentMode
import com.auraplayer.app.data.model.AppAccentPreset
import com.auraplayer.app.data.model.CoverArtStyle
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
    private val visualizerBus = VisualizerBus()
    private val playbackController: PlaybackController = mockk()

    @Test
    fun `customizing visualizer, cover style, and custom wheel colors delegates to DataStore`() = runTest {
        every { preferences.configFlow } returns flowOf(VisualizerConfig())
        every { playbackController.state } returns MutableStateFlow(PlaybackUiState())

        val viewModel = SettingsViewModel(
            preferences = preferences,
            visualizerBus = visualizerBus,
            playbackController = playbackController
        )

        viewModel.setStyle(VisualizerStyle.CIRCULAR_AURA)
        viewModel.setColorScheme(VisualizerColorScheme.NEON_CYBER)
        viewModel.setDensity(VisualizerDensity.ULTRA)
        viewModel.setSensitivity(1.8f)
        viewModel.setSmoothing(0.45f)
        viewModel.setSpreadScale(1.3f)
        viewModel.setStrokeThickness(1.4f)
        viewModel.setRotationSpeed(1.7f)
        viewModel.setGlowEnabled(false)
        viewModel.setParticlesEnabled(false)
        viewModel.setMirrorEnabled(true)
        viewModel.setPeakCapsEnabled(false)
        viewModel.setCoverArtStyle(CoverArtStyle.VINYL_DISC)
        viewModel.setAccentMode(AppAccentMode.DYNAMIC_ALBUM)
        viewModel.setAccentPreset(AppAccentPreset.CRIMSON_PULSE)
        viewModel.setCustomAppColors(0xFF112233, 0xFF445566)
        viewModel.setCustomVisualizerColors(0xFFAABBCC, 0xFFDDEEFF)
        viewModel.resetDefaults()

        coVerify { preferences.updateStyle(VisualizerStyle.CIRCULAR_AURA) }
        coVerify { preferences.updateColorScheme(VisualizerColorScheme.NEON_CYBER) }
        coVerify { preferences.updateDensity(VisualizerDensity.ULTRA) }
        coVerify { preferences.updateSensitivity(1.8f) }
        coVerify { preferences.updateSmoothing(0.45f) }
        coVerify { preferences.updateSpreadScale(1.3f) }
        coVerify { preferences.updateStrokeThickness(1.4f) }
        coVerify { preferences.updateRotationSpeed(1.7f) }
        coVerify { preferences.updateGlowEnabled(false) }
        coVerify { preferences.updateParticlesEnabled(false) }
        coVerify { preferences.updateMirrorEnabled(true) }
        coVerify { preferences.updatePeakCapsEnabled(false) }
        coVerify { preferences.updateCoverArtStyle(CoverArtStyle.VINYL_DISC) }
        coVerify { preferences.updateAccentMode(AppAccentMode.DYNAMIC_ALBUM) }
        coVerify { preferences.updateAccentPreset(AppAccentPreset.CRIMSON_PULSE) }
        coVerify { preferences.updateCustomAppColors(0xFF112233, 0xFF445566) }
        coVerify { preferences.updateCustomVisualizerColors(0xFFAABBCC, 0xFFDDEEFF) }
        coVerify { preferences.resetToDefaults() }
    }
}
