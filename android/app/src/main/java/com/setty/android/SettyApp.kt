package com.setty.android

import android.app.Application
import com.setty.android.data.AppContainer
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SettyApp : Application() {
    lateinit var appContainer: AppContainer
    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
    }
}
