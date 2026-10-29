package com.setty.android.devtools.update

import android.content.Context
import android.util.Log
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.testing.FakeAppUpdateManager
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object DevToolsAppUpdate {
    private const val TAG = "AppUpdate"
    private const val FAKE_AVAILABLE_VERSION_CODE = 9999
    private const val POLL_MILLIS = 1_000L

    fun create(context: Context, scenario: UpdateScenario): AppUpdateManager {
        val type = scenario.updateType ?: return AppUpdateManagerFactory.create(context)
        val fake = FakeAppUpdateManager(context).apply { setUpdateAvailable(FAKE_AVAILABLE_VERSION_CODE, type) }
        drive(fake, scenario)
        return fake
    }

    // Fake는 화면을 띄우지 않으므로, 앱이 업데이트를 시작하면 사용자가 고른 것처럼 상태를 넘겨 준다
    private fun drive(fake: FakeAppUpdateManager, scenario: UpdateScenario) {
        MainScope().launch {
            while (isActive) {
                runCatching {
                    if (fake.isConfirmationDialogVisible || fake.isImmediateFlowVisible) {
                        if (scenario.userAccepts) {
                            fake.userAcceptsUpdate()
                            fake.downloadStarts()
                            fake.downloadCompletes()
                        } else {
                            fake.userRejectsUpdate()
                        }
                        Log.d(TAG, "fake ${scenario.name} handled")
                    }
                    if (fake.isInstallSplashScreenVisible) {
                        fake.installCompletes()
                        Log.d(TAG, "fake install completed")
                    }
                }.onFailure { Log.w(TAG, "fake step failed", it) }
                delay(POLL_MILLIS)
            }
        }
    }
}
