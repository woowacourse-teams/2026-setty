package com.setty.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.google.android.play.core.appupdate.AppUpdateManager
import com.setty.android.data.RemoteConfigRepository
import com.setty.android.feature.remoteconfig.RemoteConfigGate
import com.setty.android.feature.update.AppUpdateGate
import com.setty.android.navigation.DeepLink
import com.setty.android.navigation.SettyNavHost
import com.setty.android.navigation.parseDeepLink
import com.setty.android.push.requestNotificationPermissionIfNeeded
import com.setty.android.ui.theme.SettyTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var remoteConfigRepository: RemoteConfigRepository

    @Inject
    lateinit var appUpdateManager: AppUpdateManager

    private val userRepository get() = (application as SettyApp).appContainer.userRepository

    /** 앱이 떠 있는 동안(singleTop) 새로 들어온 딥링크. 화면이 받기 전에 들어와도 잃지 않게 버퍼에 둔다. */
    private val newDeepLinks = Channel<DeepLink>(Channel.BUFFERED)
    private val newDeepLinkFlow = newDeepLinks.receiveAsFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        // 재생성(회전, 프로세스 종료 후 복원)이면 백스택이 이미 복원되므로 처음 받은 링크를 다시 적용하지 않는다
        val startDeepLink = if (savedInstanceState == null) parseDeepLink(intent?.dataString) else null
        val isLoggedInAtStart = startDeepLink != null && runBlocking { userRepository.hasCredential() }
        addOnNewIntentListener { newIntent ->
            parseDeepLink(newIntent.dataString)?.let { newDeepLinks.trySend(it) }
        }
        setContent {
            SettyTheme {
                RemoteConfigGate(remoteConfigRepository = remoteConfigRepository) {
                    AppUpdateGate(
                        remoteConfigRepository = remoteConfigRepository,
                        appUpdateManager = appUpdateManager,
                    ) {
                        SettyNavHost(
                            startDeepLink = startDeepLink,
                            isLoggedInAtStart = isLoggedInAtStart,
                            newDeepLinks = newDeepLinkFlow,
                            isLoggedIn = userRepository::hasCredential,
                        )
                    }
                }
            }
        }
    }
}
