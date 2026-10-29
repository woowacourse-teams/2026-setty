package com.setty.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.google.android.play.core.appupdate.AppUpdateManager
import com.setty.android.data.RemoteConfigRepository
import com.setty.android.feature.remoteconfig.RemoteConfigGate
import com.setty.android.feature.update.AppUpdateGate
import com.setty.android.navigation.SettyNavHost
import com.setty.android.push.requestNotificationPermissionIfNeeded
import com.setty.android.ui.theme.SettyTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var remoteConfigRepository: RemoteConfigRepository

    @Inject
    lateinit var appUpdateManager: AppUpdateManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        val startFurnitureId = intent.getLongExtra(EXTRA_FURNITURE_ID, -1L).takeIf { it > 0 }
        setContent {
            SettyTheme {
                RemoteConfigGate(remoteConfigRepository = remoteConfigRepository) {
                    AppUpdateGate(
                        remoteConfigRepository = remoteConfigRepository,
                        appUpdateManager = appUpdateManager,
                    ) {
                        SettyNavHost(startFurnitureId = startFurnitureId)
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_FURNITURE_ID = "furnitureId"
    }
}
