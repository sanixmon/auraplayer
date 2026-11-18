package com.auraplayer.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.auraplayer.app.data.model.AppAccentMode
import com.auraplayer.app.data.model.AppAccentPreset
import com.auraplayer.app.data.model.CoverArtStyle
import com.auraplayer.app.data.model.VisualizerColorScheme
import com.auraplayer.app.data.model.VisualizerStyle
import com.auraplayer.app.ui.components.CoverArtView
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
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Appearance", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = viewModel::resetDefaults) {
                        Icon(
                            imageVector = Icons.Rounded.RestartAlt,
                            contentDescription = "Reset"
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
            // Live Stage Preview: Cover Art Style + Avee/vizz.fm Visualizer + App Theme Accent
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 4.dp
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    VisualizerCanvas(
                        frame = frame,
                        config = config,
                        isPlaying = isPlaying,
                        demoWhenIdle = true,
                        centerRingRadiusRatio = 0.25f,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp)
                    )
                    CoverArtView(
                        track = currentTrack,
                        style = config.coverArtStyle,
                        isPlaying = true,
                        bassEnergy = if (isPlaying) frame.bassEnergy else 0.25f,
                        size = 112.dp
                    )
                }
            }

            // App Theme Color + Cover Style
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
                    Text("App Color", style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = config.accentMode == AppAccentMode.DYNAMIC_ALBUM,
                            onClick = { viewModel.setAccentMode(AppAccentMode.DYNAMIC_ALBUM) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = { Text("Dynamic") }
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppAccentPreset.entries.forEach { preset ->
                                val isSelected = config.accentMode == AppAccentMode.CUSTOM &&
                                    config.accentPreset == preset
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(
                                                listOf(
                                                    Color(preset.primaryHex),
                                                    Color(preset.secondaryHex)
                                                )
                                            )
                                        )
                                        .then(
                                            if (isSelected) {
                                                Modifier.border(2.dp, Color.White, CircleShape)
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .clickable { viewModel.setAccentPreset(preset) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = preset.displayName,
                                            tint = Color.Black,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider()

                    Text("Cover Art", style = MaterialTheme.typography.titleMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        CoverArtStyle.entries.forEach { style ->
                            FilterChip(
                                selected = config.coverArtStyle == style,
                                onClick = { viewModel.setCoverArtStyle(style) },
                                label = { Text(style.displayName) }
                            )
                        }
                    }
                }
            }

            // 5 Visualizer Controls
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
                    // 1. Style
                    Text("Visualizer", style = MaterialTheme.typography.titleMedium)
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

                    // 2. Colors
                    Text("Colors", style = MaterialTheme.typography.titleMedium)
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

                    // 3. Intensity
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Intensity", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = String.format(Locale.US, "%.1fx", config.sensitivity),
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

                    // 4. Smoothness
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Smoothness", style = MaterialTheme.typography.titleMedium)
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

                    // 5. Glow
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Glow", style = MaterialTheme.typography.titleMedium)
                        Switch(
                            checked = config.glowEnabled,
                            onCheckedChange = viewModel::setGlowEnabled
                        )
                    }
                }
            }

            Spacer(Modifier.height(96.dp))
        }
    }
}
