package com.aksworns22.setty

import android.app.Application
import com.aksworns22.setty.data.AppContainer

class SettyApp : Application() {
    lateinit var appContainer: AppContainer
    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
    }
}
