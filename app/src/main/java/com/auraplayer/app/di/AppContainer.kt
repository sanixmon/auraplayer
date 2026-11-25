package com.auraplayer.app.di

import android.content.Context
import androidx.room.Room
import com.auraplayer.app.data.backup.AuraBackupManager
import com.auraplayer.app.data.local.db.AuraDatabase
import com.auraplayer.app.data.local.prefs.VisualizerPreferencesDataSource
import com.auraplayer.app.data.repository.MusicRepository
import com.auraplayer.app.data.repository.PlaylistRepository
import com.auraplayer.app.data.scanner.AndroidLocalAudioScanner
import com.auraplayer.app.data.updater.AppUpdateManager
import com.auraplayer.app.playback.PlaybackController
import com.auraplayer.app.playback.VisualizerBus

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    private val database: AuraDatabase by lazy {
        Room.databaseBuilder(
            appContext,
            AuraDatabase::class.java,
            "aura_player.db"
        ).fallbackToDestructiveMigration().build()
    }

    val visualizerBus: VisualizerBus by lazy { VisualizerBus() }

    val visualizerPreferences: VisualizerPreferencesDataSource by lazy {
        VisualizerPreferencesDataSource(appContext)
    }

    val musicRepository: MusicRepository by lazy {
        MusicRepository(
            trackDao = database.trackDao(),
            watchedFolderDao = database.watchedFolderDao(),
            scanner = AndroidLocalAudioScanner(appContext)
        )
    }

    val playlistRepository: PlaylistRepository by lazy {
        PlaylistRepository(playlistDao = database.playlistDao())
    }

    val backupManager: AuraBackupManager by lazy {
        AuraBackupManager(
            context = appContext,
            trackDao = database.trackDao(),
            playlistDao = database.playlistDao(),
            visualizerPreferences = visualizerPreferences
        )
    }

    val playbackController: PlaybackController by lazy {
        PlaybackController(appContext)
    }

    val appUpdateManager: AppUpdateManager by lazy {
        AppUpdateManager(appContext)
    }
}
