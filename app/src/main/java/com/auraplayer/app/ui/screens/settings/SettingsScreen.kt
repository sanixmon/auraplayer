package com.auraplayer.app.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.East
import androidx.compose.material.icons.rounded.Flare
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.North
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.South
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.West
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.auraplayer.app.BuildConfig
import com.auraplayer.app.data.model.AppAccentMode
import com.auraplayer.app.data.model.AppAccentPreset
import com.auraplayer.app.data.model.CoverArtStyle
import com.auraplayer.app.data.model.EmberDirection
import com.auraplayer.app.data.model.Track
import com.auraplayer.app.data.model.VisualizerColorScheme
import com.auraplayer.app.data.model.VisualizerConfig
import com.auraplayer.app.data.model.VisualizerDensity
import com.auraplayer.app.data.model.VisualizerFillMode
import com.auraplayer.app.data.model.VisualizerFrame
import com.auraplayer.app.data.model.VisualizerStyle
import com.auraplayer.app.data.model.VisualizerTuningPreset
import com.auraplayer.app.data.updater.AppUpdateInfo
import com.auraplayer.app.data.updater.ChangelogRelease
import com.auraplayer.app.playback.VisualizerBus
import com.auraplayer.app.ui.components.CoverArtView
import com.auraplayer.app.ui.components.DualColorWheelDialog
import com.auraplayer.app.ui.components.VisualizerCanvas
import com.auraplayer.app.ui.components.toPalette
import com.auraplayer.app.ui.theme.AccentColors
import com.auraplayer.app.ui.theme.LocalAlbumAccentColors
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

private enum class ColorPickerTarget {
    APP_COLOR,
    VISUALIZER_COLOR
}

private enum class SettingsCategory(
    val title: String,
    val icon: ImageVector
) {
    THEME_AND_COVER("Theme & Cover", Icons.Rounded.Palette),
    VISUALIZER_STYLE("Visualizer Style", Icons.Rounded.GraphicEq),
    BEAT_AND_AUDIO("Beat & Audio Tuning", Icons.Rounded.Tune),
    MOTION_AND_EFFECTS("Motion & Effects", Icons.Rounded.AutoAwesome),
    UPDATES_AND_CHANGELOG("Updates & Changelog", Icons.Rounded.SystemUpdate)
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
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val bundledChangelog = remember(viewModel) { viewModel.bundledChangelog }

    var activeCategory by rememberSaveable { mutableStateOf<SettingsCategory?>(null) }
    var activeColorPicker by remember { mutableStateOf<ColorPickerTarget?>(null) }
    var showResetAllDialog by remember { mutableStateOf(false) }

    val dynamicAccent = LocalAlbumAccentColors.current
    val context = LocalContext.current

    BackHandler(enabled = activeCategory != null) {
        activeCategory = null
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = activeCategory?.title ?: "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    if (activeCategory != null) {
                        IconButton(onClick = { activeCategory = null }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                },
                actions = {
                    val currentCategory = activeCategory
                    if (currentCategory == null) {
                        IconButton(onClick = { showResetAllDialog = true }) {
                            Icon(
                                imageVector = Icons.Rounded.RestartAlt,
                                contentDescription = "Reset all settings"
                            )
                        }
                    } else if (currentCategory != SettingsCategory.UPDATES_AND_CHANGELOG) {
                        IconButton(
                            onClick = {
                                when (currentCategory) {
                                    SettingsCategory.THEME_AND_COVER -> viewModel.resetThemeAndCover()
                                    SettingsCategory.VISUALIZER_STYLE -> viewModel.resetVisualizerStyle()
                                    SettingsCategory.BEAT_AND_AUDIO -> viewModel.resetBeatAndAudio()
                                    SettingsCategory.MOTION_AND_EFFECTS -> viewModel.resetMotionAndEffects()
                                    SettingsCategory.UPDATES_AND_CHANGELOG -> Unit
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.RestartAlt,
                                contentDescription = "Reset category"
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        AnimatedContent(
            targetState = activeCategory,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            transitionSpec = {
                if (targetState != null && initialState == null) {
                    (slideInHorizontally(tween(240)) { it / 4 } + fadeIn(tween(220)))
                        .togetherWith(slideOutHorizontally(tween(220)) { -it / 6 } + fadeOut(tween(160)))
                } else {
                    (slideInHorizontally(tween(240)) { -it / 4 } + fadeIn(tween(220)))
                        .togetherWith(slideOutHorizontally(tween(220)) { it / 6 } + fadeOut(tween(160)))
                }
            },
            label = "settings_category_navigation"
        ) { category ->
            if (category == null) {
                SettingsCategoryHome(
                    config = config,
                    updateState = updateState,
                    onOpenCategory = { activeCategory = it }
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (category != SettingsCategory.UPDATES_AND_CHANGELOG) {
                        StickyLivePreviewStage(
                            frame = frame,
                            config = config,
                            currentTrack = currentTrack,
                            isPlaying = isPlaying,
                            visualizerBus = viewModel.visualizerBus,
                            showBeatBadge = category == SettingsCategory.BEAT_AND_AUDIO
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        when (category) {
                            SettingsCategory.THEME_AND_COVER -> ThemeAndCoverSection(
                                config = config,
                                onSelectDynamicAccent = {
                                    viewModel.setAccentMode(AppAccentMode.DYNAMIC_ALBUM)
                                },
                                onSelectAccentPreset = viewModel::setAccentPreset,
                                onOpenAppColorPicker = {
                                    activeColorPicker = ColorPickerTarget.APP_COLOR
                                },
                                onSelectCoverArtStyle = viewModel::setCoverArtStyle
                            )

                            SettingsCategory.VISUALIZER_STYLE -> VisualizerStyleSection(
                                config = config,
                                dynamicAccent = dynamicAccent,
                                onSelectStyle = viewModel::setStyle,
                                onSelectEmberDirection = viewModel::setEmberDirection,
                                onSelectFillMode = viewModel::setFillMode,
                                onSelectColorScheme = { scheme ->
                                    if (scheme == VisualizerColorScheme.CUSTOM) {
                                        activeColorPicker = ColorPickerTarget.VISUALIZER_COLOR
                                    } else {
                                        viewModel.setColorScheme(scheme)
                                    }
                                },
                                onSelectDensity = viewModel::setDensity
                            )

                            SettingsCategory.BEAT_AND_AUDIO -> BeatAndAudioTuningSection(
                                config = config,
                                onApplyPreset = viewModel::applyTuningPreset,
                                onBeatPunchChange = viewModel::setBeatPunch,
                                onBeatSensitivityChange = viewModel::setBeatSensitivity,
                                onBassBoostChange = viewModel::setBassBoost,
                                onSensitivityChange = viewModel::setSensitivity,
                                onSmoothingChange = viewModel::setSmoothing,
                                onSyncOffsetChange = viewModel::setSyncOffsetMs
                            )

                            SettingsCategory.MOTION_AND_EFFECTS -> MotionAndEffectsSection(
                                config = config,
                                onSpeedChange = viewModel::setRotationSpeed,
                                onSpreadChange = viewModel::setSpreadScale,
                                onThicknessChange = viewModel::setStrokeThickness,
                                onToggleGlow = { viewModel.setGlowEnabled(!config.glowEnabled) },
                                onToggleParticles = { viewModel.setParticlesEnabled(!config.particlesEnabled) },
                                onTogglePeakCaps = { viewModel.setPeakCapsEnabled(!config.peakCapsEnabled) },
                                onToggleMirror = { viewModel.setMirrorEnabled(!config.mirrorEnabled) }
                            )

                            SettingsCategory.UPDATES_AND_CHANGELOG -> UpdatesAndChangelogSection(
                                updateState = updateState,
                                bundledChangelog = bundledChangelog,
                                onCheckForUpdates = viewModel::checkForUpdates,
                                onDownloadUpdate = viewModel::downloadUpdate,
                                onInstallApk = { apkFile ->
                                    if (viewModel.canInstallPackages()) {
                                        viewModel.createInstallIntent(apkFile)?.let { intent ->
                                            runCatching { context.startActivity(intent) }
                                        }
                                    } else {
                                        viewModel.createUnknownSourcesIntent()?.let { intent ->
                                            runCatching { context.startActivity(intent) }
                                        }
                                    }
                                },
                                canInstallPackages = viewModel::canInstallPackages
                            )
                        }

                        Spacer(Modifier.height(104.dp))
                    }
                }
            }
        }
    }

    if (showResetAllDialog) {
        AlertDialog(
            onDismissRequest = { showResetAllDialog = false },
            title = { Text("Reset All Settings?") },
            text = {
                Text(
                    "Restore Theme, Visualizer Style, Beat & Audio Tuning, and Effects back to their default values."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resetDefaults()
                        showResetAllDialog = false
                    }
                ) {
                    Text("Reset All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
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
private fun SettingsCategoryHome(
    config: VisualizerConfig,
    updateState: AppUpdateUiState,
    onOpenCategory: (SettingsCategory) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Appearance & Visualizer",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 6.dp, bottom = 2.dp)
        )

        SettingsCategoryCard(
            category = SettingsCategory.THEME_AND_COVER,
            summary = buildThemeSummary(config),
            onClick = { onOpenCategory(SettingsCategory.THEME_AND_COVER) }
        )

        SettingsCategoryCard(
            category = SettingsCategory.VISUALIZER_STYLE,
            summary = buildStyleSummary(config),
            onClick = { onOpenCategory(SettingsCategory.VISUALIZER_STYLE) }
        )

        SettingsCategoryCard(
            category = SettingsCategory.BEAT_AND_AUDIO,
            summary = buildBeatSummary(config),
            onClick = { onOpenCategory(SettingsCategory.BEAT_AND_AUDIO) }
        )

        SettingsCategoryCard(
            category = SettingsCategory.MOTION_AND_EFFECTS,
            summary = buildEffectsSummary(config),
            onClick = { onOpenCategory(SettingsCategory.MOTION_AND_EFFECTS) }
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = "App",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 6.dp, bottom = 2.dp)
        )

        val updateSummary = when (updateState) {
            is AppUpdateUiState.Available -> "Update v${updateState.info.versionName} available"
            is AppUpdateUiState.ReadyToInstall -> "v${updateState.info.versionName} ready to install"
            is AppUpdateUiState.Downloading -> "Downloading v${updateState.info.versionName}..."
            else -> "v${BuildConfig.VERSION_NAME} • Check updates & changelog"
        }

        SettingsCategoryCard(
            category = SettingsCategory.UPDATES_AND_CHANGELOG,
            summary = updateSummary,
            onClick = { onOpenCategory(SettingsCategory.UPDATES_AND_CHANGELOG) }
        )

        Spacer(Modifier.height(104.dp))
    }
}

@Composable
private fun SettingsCategoryCard(
    category: SettingsCategory,
    summary: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = category.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = category.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun StickyLivePreviewStage(
    frame: VisualizerFrame,
    config: VisualizerConfig,
    currentTrack: Track?,
    isPlaying: Boolean,
    visualizerBus: VisualizerBus,
    showBeatBadge: Boolean
) {
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
                visualizerBus = visualizerBus,
                modifier = Modifier.fillMaxSize()
            )
            CoverArtView(
                track = currentTrack,
                style = config.coverArtStyle,
                isPlaying = isPlaying,
                bassEnergy = frame.bassEnergy,
                beatPulse = frame.beatPulse,
                sensitivity = config.sensitivity,
                beatPunch = config.beatPunch,
                syncOffsetMs = config.syncOffsetMs,
                demoWhenIdle = true,
                visualizerBus = visualizerBus,
                size = 104.dp
            )

            if (showBeatBadge) {
                val pulseScale by animateFloatAsState(
                    targetValue = if (isPlaying) 1f + frame.beatPulse * 0.65f else 1f,
                    animationSpec = tween(70),
                    label = "beat_badge_dot"
                )
                val badgeText = when {
                    !isPlaying -> "DEMO"
                    frame.bpmEstimate >= 45f -> "${frame.bpmEstimate.roundToInt()} BPM"
                    else -> "LIVE BEAT"
                }
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(
                            alpha = if (isPlaying) 0.30f + frame.beatPulse * 0.55f else 0.25f
                        )
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .graphicsLayer {
                                    scaleX = pulseScale
                                    scaleY = pulseScale
                                }
                                .clip(CircleShape)
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(
                                        alpha = if (isPlaying) (0.45f + frame.beatPulse * 0.55f).coerceIn(0f, 1f) else 0.7f
                                    )
                                )
                        )
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeAndCoverSection(
    config: VisualizerConfig,
    onSelectDynamicAccent: () -> Unit,
    onSelectAccentPreset: (AppAccentPreset) -> Unit,
    onOpenAppColorPicker: () -> Unit,
    onSelectCoverArtStyle: (CoverArtStyle) -> Unit
) {
    SettingsSectionCard {
        AppColorDropdown(
            accentMode = config.accentMode,
            customPrimaryArgb = config.customAppPrimaryArgb,
            customSecondaryArgb = config.customAppSecondaryArgb,
            onSelectDynamic = onSelectDynamicAccent,
            onSelectPreset = onSelectAccentPreset,
            onOpenCustomPicker = onOpenAppColorPicker
        )

        OptionDropdown(
            label = "Cover Art",
            selectedText = config.coverArtStyle.displayName,
            options = CoverArtStyle.entries,
            optionLabel = { it.displayName },
            selectedOption = config.coverArtStyle,
            onOptionSelected = onSelectCoverArtStyle
        )
    }
}

@Composable
private fun VisualizerStyleSection(
    config: VisualizerConfig,
    dynamicAccent: AccentColors,
    onSelectStyle: (VisualizerStyle) -> Unit,
    onSelectEmberDirection: (EmberDirection) -> Unit,
    onSelectFillMode: (VisualizerFillMode) -> Unit,
    onSelectColorScheme: (VisualizerColorScheme) -> Unit,
    onSelectDensity: (VisualizerDensity) -> Unit
) {
    SettingsSectionCard {
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
                onOptionSelected = onSelectStyle,
                modifier = Modifier.weight(1f)
            )

            OptionDropdown(
                label = "Fill",
                selectedText = config.fillMode.displayName,
                options = VisualizerFillMode.entries,
                optionLabel = { it.displayName },
                selectedOption = config.fillMode,
                onOptionSelected = onSelectFillMode,
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
                onOptionSelected = onSelectEmberDirection,
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
                onSelectScheme = onSelectColorScheme,
                modifier = Modifier.weight(1f)
            )

            val densityLabel = if (config.style == VisualizerStyle.FIRE_EMBER) "Density" else "Bars"
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
                onOptionSelected = onSelectDensity,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BeatAndAudioTuningSection(
    config: VisualizerConfig,
    onApplyPreset: (VisualizerTuningPreset) -> Unit,
    onBeatPunchChange: (Float) -> Unit,
    onBeatSensitivityChange: (Float) -> Unit,
    onBassBoostChange: (Float) -> Unit,
    onSensitivityChange: (Float) -> Unit,
    onSmoothingChange: (Float) -> Unit,
    onSyncOffsetChange: (Int) -> Unit
) {
    val activePreset = config.matchingTuningPreset()

    SettingsSectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Quick Presets", style = MaterialTheme.typography.titleMedium)
            Text(
                text = activePreset?.displayName ?: "Custom",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            VisualizerTuningPreset.entries.forEach { preset ->
                FilterChip(
                    selected = activePreset == preset,
                    onClick = { onApplyPreset(preset) },
                    label = { Text(preset.displayName) }
                )
            }
        }
    }

    SettingsSectionCard {
        Text(
            text = "Beat & Response",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )

        SettingSlider(
            label = "Beat Punch",
            valueText = String.format(Locale.US, "%.2fx", config.beatPunch),
            value = config.beatPunch,
            range = 0f..2.0f,
            onValueChange = onBeatPunchChange
        )

        SettingSlider(
            label = "Beat Sensitivity",
            valueText = String.format(Locale.US, "%.2fx", config.beatSensitivity),
            value = config.beatSensitivity,
            range = 0.5f..2.0f,
            onValueChange = onBeatSensitivityChange
        )

        SettingSlider(
            label = "Bass Boost",
            valueText = String.format(Locale.US, "%.2fx", config.bassBoost),
            value = config.bassBoost,
            range = 0.5f..2.0f,
            onValueChange = onBassBoostChange
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

        SettingSlider(
            label = "Intensity",
            valueText = String.format(Locale.US, "%.1fx", config.sensitivity),
            value = config.sensitivity,
            range = 0.5f..2.5f,
            onValueChange = onSensitivityChange
        )

        SettingSlider(
            label = "Smoothness",
            valueText = "${(config.smoothing * 100).toInt()}%",
            value = config.smoothing,
            range = 0.15f..0.95f,
            onValueChange = onSmoothingChange
        )
    }

    SettingsSectionCard {
        Text(
            text = "Timing Calibration",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )

        SettingSlider(
            label = "Audio Sync",
            valueText = formatSyncOffset(config.syncOffsetMs),
            value = config.syncOffsetMs.toFloat(),
            range = -100f..350f,
            onValueChange = { raw ->
                val snapped = ((raw / 5f).roundToInt() * 5).coerceIn(-100, 350)
                onSyncOffsetChange(snapped)
            }
        )

        Text(
            text = "Use negative values if visuals feel slightly late, or positive values to match Bluetooth latency.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MotionAndEffectsSection(
    config: VisualizerConfig,
    onSpeedChange: (Float) -> Unit,
    onSpreadChange: (Float) -> Unit,
    onThicknessChange: (Float) -> Unit,
    onToggleGlow: () -> Unit,
    onToggleParticles: () -> Unit,
    onTogglePeakCaps: () -> Unit,
    onToggleMirror: () -> Unit
) {
    SettingsSectionCard {
        Text(
            text = "Motion & Geometry",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )

        SettingSlider(
            label = "Speed",
            valueText = String.format(Locale.US, "%.1fx", config.rotationSpeed),
            value = config.rotationSpeed,
            range = 0f..2.5f,
            onValueChange = onSpeedChange
        )

        SettingSlider(
            label = "Spread",
            valueText = String.format(Locale.US, "%.1fx", config.spreadScale),
            value = config.spreadScale,
            range = 0.5f..1.5f,
            onValueChange = onSpreadChange
        )

        SettingSlider(
            label = "Thickness",
            valueText = String.format(Locale.US, "%.1fx", config.strokeThickness),
            value = config.strokeThickness,
            range = 0.6f..1.8f,
            onValueChange = onThicknessChange
        )
    }

    SettingsSectionCard {
        Text("Effects", style = MaterialTheme.typography.titleMedium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FilterChip(
                selected = config.glowEnabled,
                onClick = onToggleGlow,
                label = { Text("Glow") }
            )
            FilterChip(
                selected = config.particlesEnabled,
                onClick = onToggleParticles,
                label = { Text("Particles") }
            )
            FilterChip(
                selected = config.peakCapsEnabled,
                onClick = onTogglePeakCaps,
                label = { Text("Peak Dots") }
            )
            FilterChip(
                selected = config.mirrorEnabled,
                onClick = onToggleMirror,
                label = { Text("Mirror") }
            )
        }
    }
}

@Composable
private fun UpdatesAndChangelogSection(
    updateState: AppUpdateUiState,
    bundledChangelog: List<ChangelogRelease>,
    onCheckForUpdates: () -> Unit,
    onDownloadUpdate: (AppUpdateInfo) -> Unit,
    onInstallApk: (File) -> Unit,
    canInstallPackages: () -> Boolean
) {
    SettingsSectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Aura Player", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Installed v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = "v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }

        HorizontalDivider()

        when (updateState) {
            AppUpdateUiState.Idle -> {
                Text(
                    text = "Check GitHub Releases for signed arm64-v8a updates.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FilledTonalButton(
                    onClick = onCheckForUpdates,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.size(8.dp))
                    Text("Check for Updates")
                }
            }

            AppUpdateUiState.Checking -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.5.dp
                    )
                    Text(
                        text = "Checking GitHub Releases...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            is AppUpdateUiState.UpToDate -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "You're up to date (v${updateState.currentVersion}).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                FilledTonalButton(
                    onClick = onCheckForUpdates,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.size(8.dp))
                    Text("Check Again")
                }
            }

            is AppUpdateUiState.Available -> {
                val info = updateState.info
                val sizeLabel = if (info.apkSizeBytes > 0L) {
                    String.format(Locale.US, " • %.1f MB", info.apkSizeBytes / 1_048_576f)
                } else {
                    ""
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.NewReleases,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Update v${info.versionName} available$sizeLabel",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Button(
                    onClick = { onDownloadUpdate(info) },
                    enabled = info.apkDownloadUrl != null,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        if (info.apkDownloadUrl != null) {
                            "Download & Install v${info.versionName}"
                        } else {
                            "APK Not Attached Yet"
                        }
                    )
                }
            }

            is AppUpdateUiState.Downloading -> {
                val pct = (updateState.progress * 100f).roundToInt().coerceIn(0, 100)
                Text(
                    text = "Downloading v${updateState.info.versionName}... $pct%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                LinearProgressIndicator(
                    progress = { updateState.progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape)
                )
            }

            is AppUpdateUiState.ReadyToInstall -> {
                val canInstall = canInstallPackages()
                Text(
                    text = if (canInstall) {
                        "v${updateState.info.versionName} downloaded and ready to install."
                    } else {
                        "Grant \"Install unknown apps\" permission first, then tap Install again."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Button(
                    onClick = { onInstallApk(updateState.apkFile) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SystemUpdate,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(if (canInstall) "Install v${updateState.info.versionName}" else "Allow App Installs")
                }
            }

            is AppUpdateUiState.Error -> {
                Text(
                    text = updateState.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                FilledTonalButton(
                    onClick = onCheckForUpdates,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.size(8.dp))
                    Text("Retry")
                }
            }
        }
    }

    // Remote release changelog wrapper when an update is found
    val remoteReleaseNotes = when (updateState) {
        is AppUpdateUiState.Available -> updateState.info.releaseNotes
        is AppUpdateUiState.Downloading -> updateState.info.releaseNotes
        is AppUpdateUiState.ReadyToInstall -> updateState.info.releaseNotes
        else -> null
    }
    if (remoteReleaseNotes != null && remoteReleaseNotes.sections.isNotEmpty()) {
        Text(
            text = "What's New in Update",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 6.dp)
        )
        ChangelogReleaseCard(release = remoteReleaseNotes, isHighlighted = true)
    }

    // Bundled Changelog Wrapper (offline-ready)
    if (bundledChangelog.isNotEmpty()) {
        Text(
            text = "Changelog",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 6.dp)
        )
        bundledChangelog.forEachIndexed { index, release ->
            ChangelogReleaseCard(release = release, isHighlighted = index == 0)
        }
    }
}

@Composable
private fun ChangelogReleaseCard(
    release: ChangelogRelease,
    isHighlighted: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = 1.dp,
            color = if (isHighlighted) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.40f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (isHighlighted) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    }
                ) {
                    Text(
                        text = "v${release.version}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isHighlighted) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                if (release.date.isNotBlank()) {
                    Text(
                        text = release.date,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            release.sections.forEach { section ->
                val sectionColor = when (section.title.lowercase(Locale.US)) {
                    "added" -> MaterialTheme.colorScheme.primary
                    "fixed" -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.secondary
                }
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.labelMedium,
                        color = sectionColor
                    )
                    section.items.forEach { item ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 7.dp)
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(sectionColor.copy(alpha = 0.85f))
                            )
                            Text(
                                text = item,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    content: @Composable ColumnScope.() -> Unit
) {
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
            content()
        }
    }
}

private fun buildThemeSummary(config: VisualizerConfig): String {
    val accentLabel = when {
        config.accentMode == AppAccentMode.DYNAMIC_ALBUM -> "Dynamic (Album)"
        else -> AppAccentPreset.entries.firstOrNull {
            it.primaryHex == config.customAppPrimaryArgb &&
                it.secondaryHex == config.customAppSecondaryArgb
        }?.displayName ?: "Custom"
    }
    return "$accentLabel • ${config.coverArtStyle.displayName}"
}

private fun buildStyleSummary(config: VisualizerConfig): String {
    val stylePart = if (config.style == VisualizerStyle.FIRE_EMBER) {
        "${config.style.displayName} (${config.emberDirection.displayName})"
    } else {
        config.style.displayName
    }
    val densityPart = if (config.style == VisualizerStyle.FIRE_EMBER) {
        config.density.emberCountLabel()
    } else {
        "${config.density.displayName} bands"
    }
    return "$stylePart • ${config.fillMode.displayName} • ${config.colorScheme.displayName} • $densityPart"
}

private fun buildBeatSummary(config: VisualizerConfig): String {
    val presetName = config.matchingTuningPreset()?.displayName ?: "Custom"
    val punchStr = String.format(Locale.US, "%.1fx", config.beatPunch)
    return "$presetName • Punch $punchStr • Sync ${formatSyncOffset(config.syncOffsetMs)}"
}

private fun buildEffectsSummary(config: VisualizerConfig): String {
    val activeEffects = buildList {
        if (config.glowEnabled) add("Glow")
        if (config.particlesEnabled) add("Particles")
        if (config.peakCapsEnabled) add("Peak Dots")
        if (config.mirrorEnabled) add("Mirror")
    }
    val effectsText = if (activeEffects.isEmpty()) "No effects" else activeEffects.joinToString(", ")
    return String.format(Locale.US, "Speed %.1fx • %s", config.rotationSpeed, effectsText)
}

private fun formatSyncOffset(offsetMs: Int): String = when {
    offsetMs > 0 -> "+$offsetMs ms"
    offsetMs < 0 -> "$offsetMs ms"
    else -> "0 ms"
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
