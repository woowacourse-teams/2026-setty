package com.setty.android.devtools.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.setty.android.devtools.remoteconfig.RemoteConfigOverrideStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun RemoteConfigPanel(
    remoteConfig: FirebaseRemoteConfig,
    overrideStore: RemoteConfigOverrideStore,
) {
    // 다시 받거나 덮어쓰기를 지우면 올려서 화면 값을 새로 읽는다
    var version by remember { mutableIntStateOf(0) }
    val values = remember(version) { remoteConfig.all.toSortedMap() }
    val info = remember(version) { remoteConfig.info }
    var isFetching by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("마지막으로 받은 시각 : ${formatFetchTime(info.fetchTimeMillis)}")
        Text("마지막 결과 : ${fetchStatusLabel(info.lastFetchStatus)}")
        values.forEach { (key, value) ->
            var text by remember(key, version) { mutableStateOf(overrideStore.get(key).orEmpty()) }
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    overrideStore.set(key, it)
                },
                label = { Text(key) },
                placeholder = { Text("비워 두면 원격 값") },
                supportingText = { Text("적용된 값 : ${value.asString()} (${sourceLabel(value.source)})") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = !isFetching,
                onClick = {
                    isFetching = true
                    remoteConfig.fetchAndActivate().addOnCompleteListener {
                        isFetching = false
                        version++
                    }
                },
            ) { Text("지금 다시 받기") }
            OutlinedButton(onClick = {
                overrideStore.clear()
                version++
            }) { Text("덮어쓰기 지우기") }
        }
        Text("덮어쓴 값은 앱을 다시 시작해야 반영돼요.", style = MaterialTheme.typography.bodySmall)
    }
}

private fun formatFetchTime(millis: Long): String =
    if (millis <= 0) "아직 없음" else SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA).format(Date(millis))

private fun fetchStatusLabel(status: Int): String = when (status) {
    FirebaseRemoteConfig.LAST_FETCH_STATUS_SUCCESS -> "성공"
    FirebaseRemoteConfig.LAST_FETCH_STATUS_FAILURE -> "실패"
    FirebaseRemoteConfig.LAST_FETCH_STATUS_THROTTLED -> "요청 제한"
    else -> "아직 받지 않음"
}

private fun sourceLabel(source: Int): String = when (source) {
    FirebaseRemoteConfig.VALUE_SOURCE_REMOTE -> "원격"
    FirebaseRemoteConfig.VALUE_SOURCE_DEFAULT -> "기본값"
    else -> "없음"
}
