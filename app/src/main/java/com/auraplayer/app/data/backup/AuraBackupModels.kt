package com.auraplayer.app.data.backup

import com.auraplayer.app.data.model.EmberDirection
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
    val syncOffsetMs: Int? = null
) {
    fun toDomain(): VisualizerConfig {
        val defaults = VisualizerConfig()
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
            syncOffsetMs = (syncOffsetMs ?: defaults.syncOffsetMs).coerceIn(-100, 350),
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
            syncOffsetMs = config.syncOffsetMs
        )
    }
}

data class BackupImportResult(
    val playlistsImported: Int,
    val tracksLinked: Int,
    val tracksPlaceholderUnavailable: Int,
    val visualizerRestored: Boolean
)
