package com.auraplayer.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.auraplayer.app.data.artwork.TrackArtworkFetcher
import com.auraplayer.app.data.artwork.TrackArtworkKeyer
import com.auraplayer.app.di.AppContainer

class AuraApplication : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(TrackArtworkKeyer())
                add(TrackArtworkFetcher.Factory())
            }
            .crossfade(true)
            .build()
    }
}
