package com.setty.android.feature.update

import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.setty.android.data.awaitOrNull

class PlayUpdateController(
    private val appUpdateManager: AppUpdateManager,
) {
    var isDownloaded by mutableStateOf(false)
        private set

    private val installListener = InstallStateUpdatedListener { state ->
        Log.d(TAG, "installStatus=${state.installStatus()}")
        if (state.installStatus() == InstallStatus.DOWNLOADED) isDownloaded = true
    }

    fun register() = appUpdateManager.registerListener(installListener)

    fun unregister() = appUpdateManager.unregisterListener(installListener)

    // Play에서 설치하지 않았거나 Play에 새 버전이 없으면 null이다
    suspend fun findUpdate(@AppUpdateType type: Int): AppUpdateInfo? {
        val info = appUpdateManager.appUpdateInfo.awaitOrNull() ?: return null
        Log.d(
            TAG,
            "availability=${info.updateAvailability()} available=${info.availableVersionCode()} " +
                "type=$type allowed=${info.isUpdateTypeAllowed(type)}",
        )
        val isAvailable = info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE ||
            info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
        return info.takeIf { isAvailable && it.isUpdateTypeAllowed(type) }
    }

    fun start(
        info: AppUpdateInfo,
        launcher: ActivityResultLauncher<IntentSenderRequest>,
        @AppUpdateType type: Int,
    ) {
        Log.d(TAG, "start type=$type")
        appUpdateManager.startUpdateFlowForResult(info, launcher, AppUpdateOptions.newBuilder(type).build())
    }

    fun completeUpdate() {
        appUpdateManager.completeUpdate()
    }

    companion object {
        const val TAG = "AppUpdate"
    }
}
