package com.aksworns22.setty.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "credential_token")

class TokenLocalDataSource(
    private val context: Context
) {
    fun getCredentialToken(): Flow<String> {
        return context.dataStore.data.map { preferences ->
            preferences[CREDENTIAL_TOKEN] ?: ""
        }
    }

    suspend fun writeCredentialToken(token: String) {
        context.dataStore.updateData {
            it.toMutablePreferences().also { preferences ->
                preferences[CREDENTIAL_TOKEN] = token
            }
        }
    }

    companion object {
        private val CREDENTIAL_TOKEN = stringPreferencesKey("credential_token")
    }
}
