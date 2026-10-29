package com.setty.android.devtools.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.setty.android.devtools.remoteconfig.RemoteConfigOverrideStore
import com.setty.android.devtools.update.UpdateScenario
import com.setty.android.devtools.update.UpdateScenarioStore

@Composable
internal fun UpdateScenarioPanel(
    store: UpdateScenarioStore,
    overrideStore: RemoteConfigOverrideStore,
) {
    var selected by remember { mutableStateOf(store.current()) }
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 원격 설정 덮어쓰기로 판정 상황을 만든다
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { overrideStore.set(KEY_MIN_SUPPORTED_VERSION_CODE, UNREACHABLE_VERSION_CODE) }) {
                Text("최소 지원 버전 미달")
            }
            OutlinedButton(onClick = { overrideStore.set(KEY_LATEST_VERSION_CODE, UNREACHABLE_VERSION_CODE) }) {
                Text("권장 업데이트")
            }
        }
        UpdateScenario.entries.forEach { scenario ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        selected = scenario
                        store.set(scenario)
                    },
            ) {
                RadioButton(selected = scenario == selected, onClick = null)
                Text(scenario.label)
            }
        }
        Text(
            "Play 업데이트는 원격 설정이 업데이트를 요구할 때만 시작돼요. 위 버튼으로 상황을 만들고 앱을 다시 시작하세요.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private const val KEY_MIN_SUPPORTED_VERSION_CODE = "min_supported_version_code"
private const val KEY_LATEST_VERSION_CODE = "latest_version_code"
private const val UNREACHABLE_VERSION_CODE = "9999"
