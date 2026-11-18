package com.auraplayer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import com.auraplayer.app.ui.navigation.AuraAppRoot
import com.auraplayer.app.ui.theme.AuraPlayerTheme

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appContainer = (application as AuraApplication).container
        appContainer.playbackController.connect()

        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            AuraPlayerTheme {
                AuraAppRoot(
                    appContainer = appContainer,
                    widthSizeClass = windowSizeClass.widthSizeClass
                )
            }
        }
    }
}
