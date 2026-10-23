package com.setty.android.devtools.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.setty.android.devtools.model.AppBuildInfo
import com.setty.android.devtools.scenario.FailureScenario

@Composable
internal fun DevToolsScreen(
    buildInfo: AppBuildInfo,
    enabledScenarios: Set<FailureScenario>,
    onScenarioToggle: (FailureScenario, Boolean) -> Unit,
    onRestartClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("세티 개발자 도구") }) }
    ) { innerPadding ->
        LazyColumn(contentPadding = innerPadding) {
            item { SectionTitle("빌드 정보") }
            item { Text("버전 : ${buildInfo.versionName} (${buildInfo.versionCode})") }
            item { Text("빌드 타입 : ${buildInfo.buildType}") }
            item { Text("서버 환경(주소) : ${buildInfo.baseUrl}") }

            item { SectionTitle("실패 시나리오 재현") }
            items(FailureScenario.entries) { scenario ->
                Row {
                    Text("${scenario.method} ${scenario.path}")
                    Switch(
                        checked = scenario in enabledScenarios,
                        onCheckedChange = { checked -> onScenarioToggle(scenario, checked) },
                    )
                }
            }
            item {
                Button(
                    onClick = onRestartClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Text("앱 다시 시작")
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 8.dp),
    )
}

@Preview
@Composable
private fun DevToolsScreenPreview() {
    MaterialTheme {
        DevToolsScreen(
            buildInfo = AppBuildInfo(
                versionName = "1.0",
                versionCode = "1",
                buildType = "debug",
                baseUrl = "멋진 신세계",
            ),
            enabledScenarios = setOf(FailureScenario.LISTINGS_SERVER_ERROR),
            onScenarioToggle = { _, _ -> },
            onRestartClick = { }
        )
    }
}
