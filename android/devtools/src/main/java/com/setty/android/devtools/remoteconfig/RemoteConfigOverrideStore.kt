package com.setty.android.devtools.remoteconfig

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

// 앱 시작 직후 동기로 읽어야 해서 DataStore 대신 SharedPreferences를 쓴다
@Singleton
class RemoteConfigOverrideStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences("remote_config_overrides", Context.MODE_PRIVATE)

    fun get(key: String): String? = preferences.getString(key, null)

    // "앱 다시 시작"이 프로세스를 바로 끝내므로 디스크에 바로 쓴다
    fun set(key: String, value: String?) = preferences.edit(commit = true) {
        if (value.isNullOrBlank()) remove(key) else putString(key, value)
    }

    fun clear() = preferences.edit(commit = true) { clear() }
}
