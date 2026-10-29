package com.setty.android.feature.update

import android.content.Intent
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.install.model.AppUpdateType
import com.setty.android.BuildConfig
import com.setty.android.data.RemoteConfigRepository
import com.setty.android.data.UpdateRequirement
import kotlinx.coroutines.launch

// RemoteConfigGate 안쪽에 두어 시작 시점의 값으로 한 번만 판정한다
@Composable
fun AppUpdateGate(
    remoteConfigRepository: RemoteConfigRepository,
    appUpdateManager: AppUpdateManager,
    content: @Composable () -> Unit,
) {
    val requirement = remember {
        remoteConfigRepository.updateRequirement(BuildConfig.VERSION_CODE.toLong())
    }
    val controller = remember { PlayUpdateController(appUpdateManager) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        // 강제 업데이트를 취소해도 아래 다이얼로그가 남아 계속 막는다
        Log.d(PlayUpdateController.TAG, "flow resultCode=${result.resultCode}")
    }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // Play 확인이 끝나기 전에 권장 다이얼로그가 잠깐 보였다 사라지지 않도록 기다린다
    var isPlayChecked by rememberSaveable { mutableStateOf(false) }
    var isRecommendationHandled by rememberSaveable { mutableStateOf(false) }
    var isRestartPostponed by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(controller) {
        controller.register()
        onDispose { controller.unregister() }
    }
    LaunchedEffect(requirement) {
        when (requirement) {
            UpdateRequirement.REQUIRED -> controller.findUpdate(AppUpdateType.IMMEDIATE)
                ?.let { controller.start(it, launcher, AppUpdateType.IMMEDIATE) }

            UpdateRequirement.RECOMMENDED -> if (!isRecommendationHandled) {
                controller.findUpdate(AppUpdateType.FLEXIBLE)?.let {
                    controller.start(it, launcher, AppUpdateType.FLEXIBLE)
                    isRecommendationHandled = true
                }
            }

            UpdateRequirement.NONE -> Unit
        }
        isPlayChecked = true
    }

    // Play 업데이트를 쓸 수 있으면 Immediate로, 없으면 update_url로 보낸다
    val openUpdate: () -> Unit = {
        scope.launch {
            val info = controller.findUpdate(AppUpdateType.IMMEDIATE)
            if (info != null) {
                controller.start(info, launcher, AppUpdateType.IMMEDIATE)
            } else {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, remoteConfigRepository.updateUrl.toUri()))
                }
            }
        }
    }

    content()
    when {
        requirement == UpdateRequirement.REQUIRED -> AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            title = { Text("업데이트가 필요해요") },
            text = { Text("지금 버전은 더 이상 지원하지 않아요. 계속 쓰려면 최신 버전으로 업데이트해 주세요.") },
            confirmButton = { TextButton(onClick = openUpdate) { Text("업데이트") } },
        )

        controller.isDownloaded && !isRestartPostponed -> AlertDialog(
            onDismissRequest = { isRestartPostponed = true },
            title = { Text("새 버전을 받았어요") },
            text = { Text("다시 시작하면 새 버전이 적용돼요.") },
            confirmButton = { TextButton(onClick = controller::completeUpdate) { Text("다시 시작") } },
            dismissButton = { TextButton(onClick = { isRestartPostponed = true }) { Text("나중에") } },
        )

        requirement == UpdateRequirement.RECOMMENDED && isPlayChecked && !isRecommendationHandled -> AlertDialog(
            onDismissRequest = { isRecommendationHandled = true },
            title = { Text("새 버전이 나왔어요") },
            text = { Text("업데이트하면 새 기능과 개선 사항을 쓸 수 있어요.") },
            confirmButton = {
                TextButton(onClick = {
                    isRecommendationHandled = true
                    openUpdate()
                }) { Text("업데이트") }
            },
            dismissButton = { TextButton(onClick = { isRecommendationHandled = true }) { Text("나중에") } },
        )
    }
}
