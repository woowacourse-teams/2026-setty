package cloud.setty.setty

import android.app.Application
import cloud.setty.setty.data.AppContainer
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SettyApp : Application() {
    lateinit var appContainer: AppContainer
    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
    }
}
