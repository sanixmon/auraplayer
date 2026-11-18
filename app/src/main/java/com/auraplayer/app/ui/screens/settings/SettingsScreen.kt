package com.auraplayer.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
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
import com.auraplayer.app.data.model.VisualizerFillMode
import com.auraplayer.app.data.model.VisualizerStyle
import com.auraplayer.app.ui.components.CoverArtView
import com.auraplayer.app.ui.components.DualColorWheelDialog
import com.auraplayer.app.ui.components.VisualizerCanvas
import com.auraplayer.app.ui.components.toPalette
import com.auraplayer.app.ui.theme.AccentColors
import java.util.Locale
import kotlin.math.roundToInt

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

    val dynamicAccent = AccentColors(
        primary = MaterialTheme.colorScheme.primary,
        secondary = MaterialTheme.colorScheme.secondary
    )

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
                        centerRingRadiusDp = 58.dp,
                        visualizerBus = viewModel.visualizerBus,
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
                // App Color & Cover Art Dropdowns
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
                        AppColorDropdown(
                            accentMode = config.accentMode,
                            customPrimaryArgb = config.customAppPrimaryArgb,
                            customSecondaryArgb = config.customAppSecondaryArgb,
                            onSelectDynamic = { viewModel.setAccentMode(AppAccentMode.DYNAMIC_ALBUM) },
                            onSelectPreset = viewModel::setAccentPreset,
                            onOpenCustomPicker = { activeColorPicker = ColorPickerTarget.APP_COLOR }
                        )

                        OptionDropdown(
                            label = "Cover Art",
                            selectedText = config.coverArtStyle.displayName,
                            options = CoverArtStyle.entries,
                            optionLabel = { it.displayName },
                            selectedOption = config.coverArtStyle,
                            onOptionSelected = viewModel::setCoverArtStyle
                        )
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OptionDropdown(
                                label = "Visualizer",
                                selectedText = config.style.displayName,
                                options = VisualizerStyle.entries,
                                optionLabel = { it.displayName },
                                selectedOption = config.style,
                                onOptionSelected = viewModel::setStyle,
                                modifier = Modifier.weight(1.2f)
                            )

                            OptionDropdown(
                                label = "Fill",
                                selectedText = config.fillMode.displayName,
                                options = VisualizerFillMode.entries,
                                optionLabel = { it.displayName },
                                selectedOption = config.fillMode,
                                onOptionSelected = viewModel::setFillMode,
                                modifier = Modifier.weight(0.8f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            VisualizerColorDropdown(
                                colorScheme = config.colorScheme,
                                config = config,
                                dynamicAccent = dynamicAccent,
                                onSelectScheme = { scheme ->
                                    if (scheme == VisualizerColorScheme.CUSTOM) {
                                        activeColorPicker = ColorPickerTarget.VISUALIZER_COLOR
                                    } else {
                                        viewModel.setColorScheme(scheme)
                                    }
                                },
                                modifier = Modifier.weight(1.2f)
                            )

                            OptionDropdown(
                                label = "Bars",
                                selectedText = "${config.density.displayName} bands",
                                options = VisualizerDensity.entries,
                                optionLabel = { "${it.displayName} bands" },
                                selectedOption = config.density,
                                onOptionSelected = viewModel::setDensity,
                                modifier = Modifier.weight(0.8f)
                            )
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

                        SettingSlider(
                            label = "Audio Sync",
                            valueText = if (config.syncOffsetMs == 0) "0 ms" else "${config.syncOffsetMs} ms",
                            value = config.syncOffsetMs.toFloat(),
                            range = 0f..350f,
                            onValueChange = { raw ->
                                val snapped = ((raw / 5f).roundToInt() * 5).coerceIn(0, 350)
                                viewModel.setSyncOffsetMs(snapped)
                            }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> OptionDropdown(
    label: String,
    selectedText: String,
    options: List<T>,
    optionLabel: (T) -> String,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedText,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                val isSelected = option == selectedOption
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    trailingIcon = if (isSelected) {
                        {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        null
                    },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppColorDropdown(
    accentMode: AppAccentMode,
    customPrimaryArgb: Long,
    customSecondaryArgb: Long,
    onSelectDynamic: () -> Unit,
    onSelectPreset: (AppAccentPreset) -> Unit,
    onOpenCustomPicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    val matchingPreset = if (accentMode == AppAccentMode.CUSTOM) {
        AppAccentPreset.entries.firstOrNull {
            it.primaryHex == customPrimaryArgb && it.secondaryHex == customSecondaryArgb
        }
    } else {
        null
    }

    val currentText = when {
        accentMode == AppAccentMode.DYNAMIC_ALBUM -> "Dynamic (Album)"
        matchingPreset != null -> matchingPreset.displayName
        else -> "Custom"
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = currentText,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("App Color") },
            leadingIcon = {
                if (accentMode == AppAccentMode.DYNAMIC_ALBUM) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    ColorGradientDot(
                        start = Color(customPrimaryArgb),
                        end = Color(customSecondaryArgb)
                    )
                }
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Dynamic (Album)") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = if (accentMode == AppAccentMode.DYNAMIC_ALBUM) {
                    {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    null
                },
                onClick = {
                    onSelectDynamic()
                    expanded = false
                }
            )

            DropdownMenuItem(
                text = { Text("Custom Wheel / Hex...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Colorize,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = if (accentMode == AppAccentMode.CUSTOM && matchingPreset == null) {
                    {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    null
                },
                onClick = {
                    expanded = false
                    onOpenCustomPicker()
                }
            )

            HorizontalDivider()

            AppAccentPreset.entries.forEach { preset ->
                val isSelected = matchingPreset == preset
                DropdownMenuItem(
                    text = { Text(preset.displayName) },
                    leadingIcon = {
                        ColorGradientDot(
                            start = Color(preset.primaryHex),
                            end = Color(preset.secondaryHex)
                        )
                    },
                    trailingIcon = if (isSelected) {
                        {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        null
                    },
                    onClick = {
                        onSelectPreset(preset)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VisualizerColorDropdown(
    colorScheme: VisualizerColorScheme,
    config: com.auraplayer.app.data.model.VisualizerConfig,
    dynamicAccent: AccentColors,
    onSelectScheme: (VisualizerColorScheme) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val activePalette = colorScheme.toPalette(config = config, dynamicAccent = dynamicAccent)

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = colorScheme.displayName,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("Colors") },
            leadingIcon = {
                ColorGradientDot(start = activePalette.start, end = activePalette.end)
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            VisualizerColorScheme.entries.forEach { scheme ->
                val palette = scheme.toPalette(config = config, dynamicAccent = dynamicAccent)
                val isSelected = scheme == colorScheme
                val label = if (scheme == VisualizerColorScheme.CUSTOM) {
                    "Custom Wheel / Hex..."
                } else {
                    scheme.displayName
                }
                DropdownMenuItem(
                    text = { Text(label) },
                    leadingIcon = {
                        ColorGradientDot(start = palette.start, end = palette.end)
                    },
                    trailingIcon = if (isSelected) {
                        {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        null
                    },
                    onClick = {
                        expanded = false
                        onSelectScheme(scheme)
                    }
                )
            }
        }
    }
}

@Composable
private fun ColorGradientDot(
    start: Color,
    end: Color
) {
    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(start, end)))
            .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
    )
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
