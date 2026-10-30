package com.setty.android.devtools.scenario

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.mockScenarioDataStore: DataStore<Preferences> by preferencesDataStore(name = "mock_scenario")

@Singleton
internal class MockScenarioStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dataStore = context.mockScenarioDataStore

    val enabledScenarios: Flow<Set<FailureScenario>> = dataStore.data.map { preferences ->
        preferences[KEY_ENABLED_SCENARIO_NAMES].orEmpty()
            .mapNotNullTo(mutableSetOf()) { name ->
                FailureScenario.entries.find { it.name == name }
            }
    }

    suspend fun isEnabled(scenario: FailureScenario): Boolean =
        scenario in enabledScenarios.first()

    suspend fun setEnabled(scenario: FailureScenario, enabled: Boolean) {
        dataStore.edit { preferences ->
            val names = preferences[KEY_ENABLED_SCENARIO_NAMES].orEmpty()
            preferences[KEY_ENABLED_SCENARIO_NAMES] = if (enabled) names + scenario.name else names - scenario.name
        }
    }

    private companion object {
        val KEY_ENABLED_SCENARIO_NAMES = stringSetPreferencesKey("enabled_failure_scenario_names")
    }
}
