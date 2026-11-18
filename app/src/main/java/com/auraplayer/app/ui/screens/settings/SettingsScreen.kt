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
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material.icons.rounded.Colorize
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.auraplayer.app.data.model.VisualizerDensity
import com.auraplayer.app.data.model.VisualizerStyle
import com.auraplayer.app.ui.components.CoverArtView
import com.auraplayer.app.ui.components.DualColorWheelDialog
import com.auraplayer.app.ui.components.VisualizerCanvas
import java.util.Locale

private enum class ColorPickerTarget {
    APP_COLOR,
    VISUALIZER_COLOR
}

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

    var activeColorPicker by remember { mutableStateOf<ColorPickerTarget?>(null) }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
        ) {
            // Sticky Live Preview Stage pinned at the top while scrolling settings below
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .height(215.dp),
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
                        modifier = Modifier.fillMaxSize()
                    )
                    CoverArtView(
                        track = currentTrack,
                        style = config.coverArtStyle,
                        isPlaying = true,
                        bassEnergy = if (isPlaying) frame.bassEnergy else 0.25f,
                        size = 104.dp
                    )
                }
            }

            // Independently scrollable customization controls below the sticky preview
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // App Color + Cover Style
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("App Color", style = MaterialTheme.typography.titleMedium)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            itemVerticalAlignment = Alignment.CenterVertically
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

                            FilterChip(
                                selected = config.accentMode == AppAccentMode.CUSTOM,
                                onClick = { activeColorPicker = ColorPickerTarget.APP_COLOR },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Colorize,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = { Text("Custom") }
                            )

                            AppAccentPreset.entries.forEach { preset ->
                                val isSelected = config.accentMode == AppAccentMode.CUSTOM &&
                                    config.customAppPrimaryArgb == preset.primaryHex &&
                                    config.customAppSecondaryArgb == preset.secondaryHex
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

                // Expanded Visualizer Studio
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
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

                        Text("Colors", style = MaterialTheme.typography.titleMedium)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            VisualizerColorScheme.entries.forEach { scheme ->
                                FilterChip(
                                    selected = config.colorScheme == scheme,
                                    onClick = {
                                        if (scheme == VisualizerColorScheme.CUSTOM) {
                                            activeColorPicker = ColorPickerTarget.VISUALIZER_COLOR
                                        } else {
                                            viewModel.setColorScheme(scheme)
                                        }
                                    },
                                    leadingIcon = if (scheme == VisualizerColorScheme.CUSTOM) {
                                        {
                                            Box(
                                                modifier = Modifier
                                                    .size(14.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        Brush.linearGradient(
                                                            listOf(
                                                                Color(config.customVizPrimaryArgb),
                                                                Color(config.customVizSecondaryArgb)
                                                            )
                                                        )
                                                    )
                                            )
                                        }
                                    } else {
                                        null
                                    },
                                    label = { Text(scheme.displayName) }
                                )
                            }
                        }

                        Text("Bars", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            VisualizerDensity.entries.forEach { density ->
                                FilterChip(
                                    selected = config.density == density,
                                    onClick = { viewModel.setDensity(density) },
                                    label = { Text(density.displayName) }
                                )
                            }
                        }

                        HorizontalDivider()

                        SettingSlider(
                            label = "Intensity",
                            valueText = String.format(Locale.US, "%.1fx", config.sensitivity),
                            value = config.sensitivity,
                            range = 0.5f..2.5f,
                            onValueChange = viewModel::setSensitivity
                        )

                        SettingSlider(
                            label = "Smoothness",
                            valueText = "${(config.smoothing * 100).toInt()}%",
                            value = config.smoothing,
                            range = 0.15f..0.95f,
                            onValueChange = viewModel::setSmoothing
                        )

                        SettingSlider(
                            label = "Spread",
                            valueText = String.format(Locale.US, "%.1fx", config.spreadScale),
                            value = config.spreadScale,
                            range = 0.5f..1.5f,
                            onValueChange = viewModel::setSpreadScale
                        )

                        SettingSlider(
                            label = "Thickness",
                            valueText = String.format(Locale.US, "%.1fx", config.strokeThickness),
                            value = config.strokeThickness,
                            range = 0.6f..1.8f,
                            onValueChange = viewModel::setStrokeThickness
                        )

                        SettingSlider(
                            label = "Speed",
                            valueText = String.format(Locale.US, "%.1fx", config.rotationSpeed),
                            value = config.rotationSpeed,
                            range = 0f..2.5f,
                            onValueChange = viewModel::setRotationSpeed
                        )

                        HorizontalDivider()

                        Text("Effects", style = MaterialTheme.typography.titleMedium)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            FilterChip(
                                selected = config.glowEnabled,
                                onClick = { viewModel.setGlowEnabled(!config.glowEnabled) },
                                label = { Text("Glow") }
                            )
                            FilterChip(
                                selected = config.particlesEnabled,
                                onClick = { viewModel.setParticlesEnabled(!config.particlesEnabled) },
                                label = { Text("Particles") }
                            )
                            FilterChip(
                                selected = config.peakCapsEnabled,
                                onClick = { viewModel.setPeakCapsEnabled(!config.peakCapsEnabled) },
                                label = { Text("Peak Dots") }
                            )
                            FilterChip(
                                selected = config.mirrorEnabled,
                                onClick = { viewModel.setMirrorEnabled(!config.mirrorEnabled) },
                                label = { Text("Mirror") }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(104.dp))
            }
        }
    }

    when (activeColorPicker) {
        ColorPickerTarget.APP_COLOR -> {
            DualColorWheelDialog(
                title = "App Color",
                initialPrimaryArgb = config.customAppPrimaryArgb,
                initialSecondaryArgb = config.customAppSecondaryArgb,
                onDismiss = { activeColorPicker = null },
                onConfirm = { p, s ->
                    viewModel.setCustomAppColors(p, s)
                    activeColorPicker = null
                }
            )
        }
        ColorPickerTarget.VISUALIZER_COLOR -> {
            DualColorWheelDialog(
                title = "Visualizer Colors",
                initialPrimaryArgb = config.customVizPrimaryArgb,
                initialSecondaryArgb = config.customVizSecondaryArgb,
                onDismiss = { activeColorPicker = null },
                onConfirm = { p, s ->
                    viewModel.setCustomVisualizerColors(p, s)
                    activeColorPicker = null
                }
            )
        }
        null -> Unit
    }
}

@Composable
private fun SettingSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range
        )
    }
}
