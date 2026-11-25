package com.auraplayer.app.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.East
import androidx.compose.material.icons.rounded.Flare
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.North
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.South
import androidx.compose.material.icons.rounded.West
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.auraplayer.app.data.model.AppAccentMode
import com.auraplayer.app.data.model.AppAccentPreset
import com.auraplayer.app.data.model.CoverArtStyle
import com.auraplayer.app.data.model.EmberDirection
import com.auraplayer.app.data.model.VisualizerColorScheme
import com.auraplayer.app.data.model.VisualizerConfig
import com.auraplayer.app.data.model.VisualizerDensity
import com.auraplayer.app.data.model.VisualizerFillMode
import com.auraplayer.app.data.model.VisualizerStyle
import com.auraplayer.app.ui.components.CoverArtView
import com.auraplayer.app.ui.components.DualColorWheelDialog
import com.auraplayer.app.ui.components.VisualizerCanvas
import com.auraplayer.app.ui.components.toPalette
import com.auraplayer.app.ui.theme.AccentColors
import com.auraplayer.app.ui.theme.LocalAlbumAccentColors
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

    val dynamicAccent = LocalAlbumAccentColors.current

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
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
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
                        isPlaying = isPlaying,
                        bassEnergy = frame.bassEnergy,
                        beatPulse = frame.beatPulse,
                        sensitivity = config.sensitivity,
                        syncOffsetMs = config.syncOffsetMs,
                        demoWhenIdle = true,
                        visualizerBus = viewModel.visualizerBus,
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
                    shape = RoundedCornerShape(26.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
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
                    shape = RoundedCornerShape(26.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
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
                                modifier = Modifier.weight(1f)
                            )

                            OptionDropdown(
                                label = "Fill",
                                selectedText = config.fillMode.displayName,
                                options = VisualizerFillMode.entries,
                                optionLabel = { it.displayName },
                                selectedOption = config.fillMode,
                                onOptionSelected = viewModel::setFillMode,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        AnimatedVisibility(
                            visible = config.style == VisualizerStyle.FIRE_EMBER,
                            enter = fadeIn(tween(220)) + expandVertically(tween(220)),
                            exit = fadeOut(tween(180)) + shrinkVertically(tween(180))
                        ) {
                            OptionDropdown(
                                label = "Particle Direction",
                                selectedText = config.emberDirection.displayName,
                                options = EmberDirection.entries,
                                optionLabel = { it.displayName },
                                selectedOption = config.emberDirection,
                                onOptionSelected = viewModel::setEmberDirection,
                                optionLeadingIcon = { dir ->
                                    Icon(
                                        imageVector = dir.toIcon(),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                selectedLeadingIcon = {
                                    Icon(
                                        imageVector = config.emberDirection.toIcon(),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
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
                                modifier = Modifier.weight(1f)
                            )

                            val densityLabel = if (config.style == VisualizerStyle.FIRE_EMBER) {
                                "Density"
                            } else {
                                "Bars"
                            }
                            OptionDropdown(
                                label = densityLabel,
                                selectedText = if (config.style == VisualizerStyle.FIRE_EMBER) {
                                    config.density.emberCountLabel()
                                } else {
                                    "${config.density.displayName} bands"
                                },
                                options = VisualizerDensity.entries,
                                optionLabel = { density ->
                                    if (config.style == VisualizerStyle.FIRE_EMBER) {
                                        density.emberCountLabel()
                                    } else {
                                        "${density.displayName} bands"
                                    }
                                },
                                selectedOption = config.density,
                                onOptionSelected = viewModel::setDensity,
                                modifier = Modifier.weight(1f)
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

private fun EmberDirection.toIcon(): ImageVector = when (this) {
    EmberDirection.UP -> Icons.Rounded.North
    EmberDirection.DOWN -> Icons.Rounded.South
    EmberDirection.LEFT -> Icons.Rounded.West
    EmberDirection.RIGHT -> Icons.Rounded.East
    EmberDirection.RADIAL -> Icons.Rounded.Flare
}

private fun VisualizerDensity.emberCountLabel(): String = when (this) {
    VisualizerDensity.LOW -> "48 sparks"
    VisualizerDensity.MEDIUM -> "80 sparks"
    VisualizerDensity.HIGH -> "116 sparks"
    VisualizerDensity.ULTRA -> "160 sparks"
}

@Composable
private fun DropdownFieldTrigger(
    label: String,
    selectedText: String,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    leadingContent: (@Composable () -> Unit)? = null
) {
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(200),
        label = "dropdown_arrow_rotation"
    )
    val borderColor = if (expanded) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.30f)
    }
    val containerColor = if (expanded) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (leadingContent != null) {
                leadingContent()
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (expanded) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = selectedText,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = if (expanded) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer { rotationZ = arrowRotation }
            )
        }
    }
}

@Composable
private fun StyledDropdownMenuItem(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    DropdownMenuItem(
        modifier = Modifier
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                } else {
                    Color.Transparent
                }
            ),
        text = {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                ),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingIcon = leadingIcon,
        trailingIcon = if (isSelected) {
            {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        } else {
            null
        },
        onClick = onClick
    )
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
    modifier: Modifier = Modifier,
    selectedLeadingIcon: (@Composable () -> Unit)? = null,
    optionLeadingIcon: (@Composable (T) -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        DropdownFieldTrigger(
            label = label,
            selectedText = selectedText,
            expanded = expanded,
            leadingContent = selectedLeadingIcon,
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            matchTextFieldWidth = false,
            modifier = Modifier.widthIn(min = 188.dp),
            shape = RoundedCornerShape(18.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            shadowElevation = 10.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.30f))
        ) {
            options.forEach { option ->
                val isSelected = option == selectedOption
                StyledDropdownMenuItem(
                    text = optionLabel(option),
                    isSelected = isSelected,
                    leadingIcon = optionLeadingIcon?.let { iconProvider ->
                        { iconProvider(option) }
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
        DropdownFieldTrigger(
            label = "App Color",
            selectedText = currentText,
            expanded = expanded,
            leadingContent = {
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
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            matchTextFieldWidth = true,
            shape = RoundedCornerShape(18.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            shadowElevation = 10.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.30f))
        ) {
            StyledDropdownMenuItem(
                text = "Dynamic (Album)",
                isSelected = accentMode == AppAccentMode.DYNAMIC_ALBUM,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                },
                onClick = {
                    onSelectDynamic()
                    expanded = false
                }
            )

            StyledDropdownMenuItem(
                text = "Custom Wheel / Hex...",
                isSelected = accentMode == AppAccentMode.CUSTOM && matchingPreset == null,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Colorize,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                onClick = {
                    expanded = false
                    onOpenCustomPicker()
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp, horizontal = 12.dp))

            AppAccentPreset.entries.forEach { preset ->
                val isSelected = matchingPreset == preset
                StyledDropdownMenuItem(
                    text = preset.displayName,
                    isSelected = isSelected,
                    leadingIcon = {
                        ColorGradientDot(
                            start = Color(preset.primaryHex),
                            end = Color(preset.secondaryHex)
                        )
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
    config: VisualizerConfig,
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
        DropdownFieldTrigger(
            label = "Colors",
            selectedText = colorScheme.displayName,
            expanded = expanded,
            leadingContent = {
                ColorGradientDot(start = activePalette.start, end = activePalette.end)
            },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            matchTextFieldWidth = false,
            modifier = Modifier.widthIn(min = 216.dp),
            shape = RoundedCornerShape(18.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            shadowElevation = 10.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.30f))
        ) {
            VisualizerColorScheme.entries.forEach { scheme ->
                val palette = scheme.toPalette(config = config, dynamicAccent = dynamicAccent)
                val isSelected = scheme == colorScheme
                val label = if (scheme == VisualizerColorScheme.CUSTOM) {
                    "Custom Wheel / Hex..."
                } else {
                    scheme.displayName
                }
                StyledDropdownMenuItem(
                    text = label,
                    isSelected = isSelected,
                    leadingIcon = {
                        ColorGradientDot(start = palette.start, end = palette.end)
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
