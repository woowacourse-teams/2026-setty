package com.setty.android.devtools.update

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

// AppUpdateManager를 만들 때 동기로 읽어야 해서 SharedPreferences를 쓴다
@Singleton
class UpdateScenarioStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences("update_scenario", Context.MODE_PRIVATE)

    fun current(): UpdateScenario =
        UpdateScenario.entries.find { it.name == preferences.getString(KEY_SCENARIO, null) } ?: UpdateScenario.REAL_PLAY

    // "앱 다시 시작"이 프로세스를 바로 끝내므로 디스크에 바로 쓴다
    fun set(scenario: UpdateScenario) = preferences.edit(commit = true) { putString(KEY_SCENARIO, scenario.name) }

    private companion object {
        const val KEY_SCENARIO = "scenario"
    }
}
