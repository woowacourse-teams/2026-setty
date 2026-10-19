package cloud.setty.setty.data

import cloud.setty.setty.network.DeviceTokenDeleteRequest
import cloud.setty.setty.network.DeviceTokenRequest
import cloud.setty.setty.network.SettyNetworkApi
import cloud.setty.setty.network.apiCall
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
