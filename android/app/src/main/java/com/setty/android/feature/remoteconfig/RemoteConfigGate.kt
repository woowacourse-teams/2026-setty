package com.setty.android.feature.remoteconfig

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.setty.android.data.RemoteConfigRepository

@Composable
fun RemoteConfigGate(
    remoteConfigRepository: RemoteConfigRepository,
    content: @Composable () -> Unit,
) {
    // 화면 회전으로 다시 그려질 때 로딩을 반복하지 않도록 저장한다
    var isConfigReady by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!isConfigReady) {
            remoteConfigRepository.fetchAndActivate()
            isConfigReady = true
        }
    }
    if (isConfigReady) {
        content()
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}
