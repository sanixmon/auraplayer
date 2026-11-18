package com.auraplayer.app.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.auraplayer.app.data.model.AudioFormat
import com.auraplayer.app.data.model.VisualizerColorScheme
import com.auraplayer.app.data.model.VisualizerDensity
import com.auraplayer.app.data.model.VisualizerStyle
import com.auraplayer.app.ui.components.FormatBadge
import com.auraplayer.app.ui.components.VisualizerCanvas
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val config by viewModel.visualizerConfig.collectAsStateWithLifecycle()
    val frame by viewModel.visualizerFrame.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportFullBackup(uri.toString())
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importFullBackup(uri.toString())
        }
    }

    LaunchedEffect(statusMessage) {
        val msg = statusMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearStatusMessage()
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Visualizer & Settings", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = viewModel::resetDefaults) {
                        Icon(
                            imageVector = Icons.Rounded.RestartAlt,
                            contentDescription = "Reset visualizer defaults"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Live Preview Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 4.dp
            ) {
                VisualizerCanvas(
                    frame = frame,
                    config = config,
                    isPlaying = isPlaying,
                    demoWhenIdle = true,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                )
            }

            // Visualizer Customization Studio (8 settings)
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("Visualizer Studio", style = MaterialTheme.typography.titleLarge)

                    // 1. Style
                    Text("Style", style = MaterialTheme.typography.titleMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        VisualizerStyle.entries.forEach { style ->
                            FilterChip(
                                selected = config.style == style,
                                onClick = { viewModel.setStyle(style) },
                                label = { Text(style.displayName) }
                            )
                        }
                    }

                    // 2. Color Scheme
                    Text("Color Scheme", style = MaterialTheme.typography.titleMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        VisualizerColorScheme.entries.forEach { scheme ->
                            FilterChip(
                                selected = config.colorScheme == scheme,
                                onClick = { viewModel.setColorScheme(scheme) },
                                label = { Text(scheme.displayName) }
                            )
                        }
                    }

                    // 3. Density
                    Text("Spectrum Density", style = MaterialTheme.typography.titleMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        VisualizerDensity.entries.forEach { density ->
                            FilterChip(
                                selected = config.density == density,
                                onClick = { viewModel.setDensity(density) },
                                label = { Text(density.displayName) }
                            )
                        }
                    }

                    HorizontalDivider()

                    // 4. Sensitivity
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Sensitivity / Gain", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = String.format(Locale.US, "%.2fx", config.sensitivity),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = config.sensitivity,
                            onValueChange = viewModel::setSensitivity,
                            valueRange = 0.5f..2.5f
                        )
                    }

                    // 5. Smoothing
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Animation Smoothing", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "${(config.smoothing * 100).toInt()}%",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = config.smoothing,
                            onValueChange = viewModel::setSmoothing,
                            valueRange = 0.15f..0.95f
                        )
                    }

                    HorizontalDivider()

                    // 6. Luminous Glow
                    SettingSwitchRow(
                        title = "Luminous Glow",
                        subtitle = "Soft ambient bloom around active frequencies",
                        checked = config.glowEnabled,
                        onCheckedChange = viewModel::setGlowEnabled
                    )

                    // 7. Mirror Symmetry
                    SettingSwitchRow(
                        title = "Mirror Symmetry",
                        subtitle = "Reflect spectrum and waveforms across center axis",
                        checked = config.mirrorEnabled,
                        onCheckedChange = viewModel::setMirrorEnabled
                    )

                    // 8. Peak Caps
                    SettingSwitchRow(
                        title = "Floating Peak Caps",
                        subtitle = "Track harmonic peaks with smooth gravity decay",
                        checked = config.peakCapsEnabled,
                        onCheckedChange = viewModel::setPeakCapsEnabled
                    )
                }
            }

            // Local Aura Backup & Restore Card
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Aura Backup & Restore", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Export all Playlists and Visualizer Studio presets to a local JSON file, or restore them on a new device. Missing audio files remain as Unavailable placeholders and auto-relink after Library Scan.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { exportBackupLauncher.launch("aura_player_backup.json") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Backup, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Export All")
                        }
                        OutlinedButton(
                            onClick = {
                                importBackupLauncher.launch(arrayOf("application/json", "*/*"))
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Restore, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Restore")
                        }
                    }
                }
            }

            // Supported Audio Formats Card (21 formats via MediaCodec + Bundled LGPL FFmpeg)
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Supported Offline Formats (${AudioFormat.supportedFormats.size})",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Hardware MediaCodec runs first for battery efficiency, with bundled LGPL Media3 FFmpeg decoding ALAC, WMA, APE, AC3, DTS, WavPack, TTA, and Musepack.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AudioFormat.supportedFormats.forEach { format ->
                            FormatBadge(format = format)
                        }
                    }
                }
            }

            // Offline & Security Guarantee Card
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("100% Offline & Private", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Aura Player requests zero network permissions, disables cloud auto-backup, and processes PCM audio in-memory without microphone access.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(96.dp))
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
