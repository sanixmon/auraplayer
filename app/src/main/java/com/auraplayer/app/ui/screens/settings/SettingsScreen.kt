package com.auraplayer.app.ui.screens.settings

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.auraplayer.app.data.model.VisualizerColorScheme
import com.auraplayer.app.data.model.VisualizerStyle
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

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Visualizer", style = MaterialTheme.typography.titleLarge) },
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
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp),
                shape = RoundedCornerShape(22.dp),
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
                        .padding(14.dp)
                )
            }

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
