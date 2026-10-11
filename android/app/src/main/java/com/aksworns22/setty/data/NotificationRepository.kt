package com.aksworns22.setty.data

import com.aksworns22.setty.network.SettyNetworkApi
import com.aksworns22.setty.network.apiCall

class NotificationRepository(
    private val settyNetworkApi: SettyNetworkApi,
) {
    suspend fun ack(notificationId: Long) {
        apiCall { settyNetworkApi.ackNotification(notificationId) }
    }
}
