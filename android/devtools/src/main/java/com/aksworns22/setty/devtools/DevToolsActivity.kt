package com.aksworns22.setty.devtools

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.aksworns22.setty.devtools.model.AppBuildInfo
import com.aksworns22.setty.devtools.scenario.MockScenarioStore
import com.aksworns22.setty.devtools.ui.DevToolsScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class DevToolsActivity : ComponentActivity() {

    @Inject
    internal lateinit var scenarioStore: MockScenarioStore

    @Inject
    lateinit var buildInfo: AppBuildInfo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                val enabledScenarios by scenarioStore.enabledScenarios.collectAsStateWithLifecycle(
                    initialValue = emptySet()
                )
                DevToolsScreen(
                    buildInfo = buildInfo,
                    enabledScenarios = enabledScenarios,
                    onScenarioToggle = { scenario, enabled ->
                        lifecycleScope.launch { scenarioStore.setEnabled(scenario, enabled) }
                    },
                    onRestartClick = { restartSettyApp() }
                )
            }
        }
    }

    private fun restartSettyApp() {
        val launcherIntent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setPackage(packageName)

        val mainActivity = packageManager.queryIntentActivities(launcherIntent, 0)
            .map { it.activityInfo.name }
            .first { it != DevToolsActivity::class.java.name }

        startActivity(Intent.makeRestartActivityTask(ComponentName(packageName, mainActivity)))
        Runtime.getRuntime().exit(0)
    }
}
