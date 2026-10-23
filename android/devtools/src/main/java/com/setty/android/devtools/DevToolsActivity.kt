package com.setty.android.devtools

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
import com.setty.android.devtools.model.AppBuildInfo
import com.setty.android.devtools.scenario.MockScenarioStore
import com.setty.android.devtools.ui.DevToolsScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class DevToolsActivity : ComponentActivity() {

    @Inject
    internal lateinit var scenarioStore: MockScenarioStore

    @Inject
    lateinit var buildInfo: AppBuildInfo

    private val scenarioWriteJobs = mutableListOf<Job>()
    private var isRestarting = false

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
                        if (!isRestarting) {
                            scenarioWriteJobs += lifecycleScope.launch {
                                scenarioStore.setEnabled(scenario, enabled)
                            }
                        }
                    },
                    onRestartClick = {
                        if (!isRestarting) {
                            isRestarting = true
                            lifecycleScope.launch {
                                scenarioWriteJobs.joinAll()
                                restartSettyApp()
                            }
                        }
                    }
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
