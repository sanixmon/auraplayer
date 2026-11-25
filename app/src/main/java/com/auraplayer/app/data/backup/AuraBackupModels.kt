package com.auraplayer.app.data.backup

import com.auraplayer.app.data.model.BackgroundVisualMode
import com.auraplayer.app.data.model.EmberDirection
import com.auraplayer.app.data.model.ParticleOverlayStyle
import com.auraplayer.app.data.model.VisualizerColorScheme
import com.auraplayer.app.data.model.VisualizerConfig
import com.auraplayer.app.data.model.VisualizerDensity
import com.auraplayer.app.data.model.VisualizerStyle
import kotlinx.serialization.Serializable

@Serializable
data class AuraBackupDocument(
    val schemaVersion: Int = 1,
    val exportedAtMs: Long,
    val playlists: List<BackupPlaylist> = emptyList(),
    val visualizerConfig: BackupVisualizerConfig? = null
)

@Serializable
data class BackupPlaylist(
    val name: String,
    val description: String = "",
    val entries: List<BackupTrackRef> = emptyList()
)

@Serializable
data class BackupTrackRef(
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val format: String,
    val fingerprint: String
)

@Serializable
data class BackupVisualizerConfig(
    val style: String,
    val colorScheme: String,
    val density: String,
    val sensitivity: Float,
    val smoothing: Float,
    val glowEnabled: Boolean,
    val mirrorEnabled: Boolean,
    val peakCapsEnabled: Boolean,
    val emberDirection: String? = null,
    val customVizPrimaryArgb: Long? = null,
    val customVizSecondaryArgb: Long? = null,
    val beatPunch: Float? = null,
    val beatSensitivity: Float? = null,
    val bassBoost: Float? = null,
    val syncOffsetMs: Int? = null,
    val freqMinHz: Int? = null,
    val freqMaxHz: Int? = null,
    val backgroundMode: String? = null,
    val particleOverlay: String? = null,
    val cameraShakeIntensity: Float? = null,
    val rgbSplitIntensity: Float? = null,
    val additiveBlendEnabled: Boolean? = null,
    val vignetteEnabled: Boolean? = null,
    val sharpBarsEnabled: Boolean? = null
) {
    fun toDomain(): VisualizerConfig {
        val defaults = VisualizerConfig()
        val safeMinHz = (freqMinHz ?: defaults.freqMinHz).coerceIn(20, 500)
        val safeMaxHz = (freqMaxHz ?: defaults.freqMaxHz).coerceIn(maxOf(1000, safeMinHz + 500), 16000)
        return VisualizerConfig(
            style = VisualizerStyle.entries.firstOrNull { it.name == style } ?: VisualizerStyle.BARS,
            emberDirection = emberDirection?.let { name ->
                EmberDirection.entries.firstOrNull { it.name == name }
            } ?: defaults.emberDirection,
            colorScheme = VisualizerColorScheme.entries.firstOrNull { it.name == colorScheme }
                ?: VisualizerColorScheme.AURORA,
            density = VisualizerDensity.entries.firstOrNull { it.name == density }
                ?: VisualizerDensity.MEDIUM,
            sensitivity = sensitivity.coerceIn(0.5f, 2.5f),
            smoothing = smoothing.coerceIn(0.15f, 0.95f),
            beatPunch = (beatPunch ?: defaults.beatPunch).coerceIn(0f, 2.0f),
            beatSensitivity = (beatSensitivity ?: defaults.beatSensitivity).coerceIn(0.5f, 2.0f),
            bassBoost = (bassBoost ?: defaults.bassBoost).coerceIn(0.5f, 2.0f),
            freqMinHz = safeMinHz,
            freqMaxHz = safeMaxHz,
            syncOffsetMs = (syncOffsetMs ?: defaults.syncOffsetMs).coerceIn(-100, 350),
            backgroundMode = backgroundMode?.let { name ->
                BackgroundVisualMode.entries.firstOrNull { it.name == name }
            } ?: defaults.backgroundMode,
            particleOverlay = particleOverlay?.let { name ->
                ParticleOverlayStyle.entries.firstOrNull { it.name == name }
            } ?: defaults.particleOverlay,
            cameraShakeIntensity = (cameraShakeIntensity ?: defaults.cameraShakeIntensity).coerceIn(0f, 1.5f),
            rgbSplitIntensity = (rgbSplitIntensity ?: defaults.rgbSplitIntensity).coerceIn(0f, 1.5f),
            additiveBlendEnabled = additiveBlendEnabled ?: defaults.additiveBlendEnabled,
            vignetteEnabled = vignetteEnabled ?: defaults.vignetteEnabled,
            sharpBarsEnabled = sharpBarsEnabled ?: defaults.sharpBarsEnabled,
            glowEnabled = glowEnabled,
            mirrorEnabled = mirrorEnabled,
            peakCapsEnabled = peakCapsEnabled,
            customVizPrimaryArgb = customVizPrimaryArgb ?: defaults.customVizPrimaryArgb,
            customVizSecondaryArgb = customVizSecondaryArgb ?: defaults.customVizSecondaryArgb
        )
    }

    companion object {
        fun fromDomain(config: VisualizerConfig): BackupVisualizerConfig = BackupVisualizerConfig(
            style = config.style.name,
            colorScheme = config.colorScheme.name,
            density = config.density.name,
            sensitivity = config.sensitivity,
            smoothing = config.smoothing,
            glowEnabled = config.glowEnabled,
            mirrorEnabled = config.mirrorEnabled,
            peakCapsEnabled = config.peakCapsEnabled,
            emberDirection = config.emberDirection.name,
            customVizPrimaryArgb = config.customVizPrimaryArgb,
            customVizSecondaryArgb = config.customVizSecondaryArgb,
            beatPunch = config.beatPunch,
            beatSensitivity = config.beatSensitivity,
            bassBoost = config.bassBoost,
            syncOffsetMs = config.syncOffsetMs,
            freqMinHz = config.freqMinHz,
            freqMaxHz = config.freqMaxHz,
            backgroundMode = config.backgroundMode.name,
            particleOverlay = config.particleOverlay.name,
            cameraShakeIntensity = config.cameraShakeIntensity,
            rgbSplitIntensity = config.rgbSplitIntensity,
            additiveBlendEnabled = config.additiveBlendEnabled,
            vignetteEnabled = config.vignetteEnabled,
            sharpBarsEnabled = config.sharpBarsEnabled
        )
    }
}

data class BackupImportResult(
    val playlistsImported: Int,
    val tracksLinked: Int,
    val tracksPlaceholderUnavailable: Int,
    val visualizerRestored: Boolean
)
