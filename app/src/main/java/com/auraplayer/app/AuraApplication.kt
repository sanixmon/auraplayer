package com.auraplayer.app

import android.app.Application
import com.auraplayer.app.di.AppContainer

class AuraApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
