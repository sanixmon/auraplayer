package com.auraplayer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.auraplayer.app.data.model.VisualizerConfig
import com.auraplayer.app.ui.navigation.AuraAppRoot
import com.auraplayer.app.ui.theme.AuraPlayerTheme
import com.auraplayer.app.ui.theme.rememberResolvedAccentColors

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appContainer = (application as AuraApplication).container
        appContainer.playbackController.connect()

        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            val playbackState by appContainer.playbackController.state.collectAsStateWithLifecycle()
            val visualizerConfig by appContainer.visualizerPreferences.configFlow
                .collectAsStateWithLifecycle(initialValue = VisualizerConfig())
            val accentColors = rememberResolvedAccentColors(
                currentTrack = playbackState.currentTrack,
                config = visualizerConfig
            )

            AuraPlayerTheme(accentColors = accentColors) {
                AuraAppRoot(
                    appContainer = appContainer,
                    widthSizeClass = windowSizeClass.widthSizeClass
                )
            }
        }
    }
}
