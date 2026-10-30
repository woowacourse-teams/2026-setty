package com.setty.android.data

import android.util.Log
import com.google.firebase.remoteconfig.ConfigUpdate
import com.google.firebase.remoteconfig.ConfigUpdateListener
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.setty.android.BuildConfig
import com.setty.android.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Optional
import javax.inject.Inject
import javax.inject.Singleton

enum class UpdateRequirement { NONE, RECOMMENDED, REQUIRED }

@Singleton
class RemoteConfigRepository @Inject constructor(
    private val remoteConfig: FirebaseRemoteConfig,
    overrides: Optional<RemoteConfigOverrides>,
) {
    private val overrides: RemoteConfigOverrides? = overrides.orElse(null)

    private val _isKeywordNotificationEnabled = MutableStateFlow(false)
    val isKeywordNotificationEnabled: StateFlow<Boolean> = _isKeywordNotificationEnabled.asStateFlow()

    val updateUrl: String
        get() = string(KEY_UPDATE_URL)

    init {
        remoteConfig.setConfigSettingsAsync(
            FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(if (BuildConfig.DEBUG) 0 else FETCH_INTERVAL_SECONDS)
                .build()
        )
        remoteConfig.setDefaultsAsync(R.xml.remote_config_defaults)
        // 킬 스위치는 켜져 있는 앱에도 바로 닿아야 하므로 실시간 업데이트는 받자마자 적용한다
        remoteConfig.addOnConfigUpdateListener(object : ConfigUpdateListener {
            override fun onUpdate(configUpdate: ConfigUpdate) {
                remoteConfig.activate().addOnCompleteListener {
                    publish()
                    Log.d(TAG, "realtime updatedKeys=${configUpdate.updatedKeys} keyword=${_isKeywordNotificationEnabled.value}")
                }
            }

            override fun onError(error: FirebaseRemoteConfigException) {
                Log.w(TAG, "realtime error code=${error.code}", error)
            }
        })
    }

    // 시간 안에 받지 못하면 마지막으로 적용된 값, 그것도 없으면 기본값을 쓴다
    suspend fun fetchAndActivate() {
        val startedAt = System.currentTimeMillis()
        val activated = withTimeoutOrNull(FETCH_TIMEOUT_MILLIS) { remoteConfig.fetchAndActivate().awaitOrNull() }
        publish()
        Log.d(
            TAG,
            "activated=$activated lastFetchStatus=${remoteConfig.info.lastFetchStatus} " +
                "elapsed=${System.currentTimeMillis() - startedAt}ms " +
                "keyword=${_isKeywordNotificationEnabled.value} " +
                "min=${long(KEY_MIN_SUPPORTED_VERSION_CODE)} latest=${long(KEY_LATEST_VERSION_CODE)}",
        )
    }

    fun updateRequirement(currentVersionCode: Long): UpdateRequirement = when {
        currentVersionCode < long(KEY_MIN_SUPPORTED_VERSION_CODE) -> UpdateRequirement.REQUIRED
        currentVersionCode < long(KEY_LATEST_VERSION_CODE) -> UpdateRequirement.RECOMMENDED
        else -> UpdateRequirement.NONE
    }

    private fun publish() {
        _isKeywordNotificationEnabled.value = boolean(KEY_KEYWORD_NOTIFICATION_ENABLED)
    }

    // 개발자 도구에서 덮어쓴 값이 있으면 그 값을 먼저 쓴다
    private fun string(key: String): String = overrides?.get(key) ?: remoteConfig.getString(key)

    private fun boolean(key: String): Boolean =
        overrides?.get(key)?.toBooleanStrictOrNull() ?: remoteConfig.getBoolean(key)

    private fun long(key: String): Long = overrides?.get(key)?.toLongOrNull() ?: remoteConfig.getLong(key)

    companion object {
        private const val TAG = "RemoteConfig"
        private const val FETCH_INTERVAL_SECONDS = 3600L
        private const val FETCH_TIMEOUT_MILLIS = 3000L
        private const val KEY_KEYWORD_NOTIFICATION_ENABLED = "keyword_notification_enabled"
        private const val KEY_MIN_SUPPORTED_VERSION_CODE = "min_supported_version_code"
        private const val KEY_LATEST_VERSION_CODE = "latest_version_code"
        private const val KEY_UPDATE_URL = "update_url"
    }
}
