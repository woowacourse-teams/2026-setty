package com.aksworns22.setty.data

import com.aksworns22.setty.network.DeviceTokenDeleteRequest
import com.aksworns22.setty.network.DeviceTokenRequest
import com.aksworns22.setty.network.SettyNetworkApi
import com.aksworns22.setty.network.apiCall
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class DeviceTokenRepository(
    private val settyNetworkApi: SettyNetworkApi,
) {
    suspend fun register() {
        register(currentToken())
    }

    suspend fun register(token: String) {
        apiCall { settyNetworkApi.registerDeviceToken(DeviceTokenRequest(token, PLATFORM)) }
    }

    suspend fun unregister() {
        apiCall { settyNetworkApi.unregisterDeviceToken(DeviceTokenDeleteRequest(currentToken())) }
    }

    private suspend fun currentToken(): String = suspendCancellableCoroutine { continuation ->
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { continuation.resume(it) }
            .addOnFailureListener { continuation.resumeWithException(it) }
    }

    companion object {
        private const val PLATFORM = "ANDROID"
    }
}
