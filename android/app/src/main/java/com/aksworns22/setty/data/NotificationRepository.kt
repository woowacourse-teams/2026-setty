package com.aksworns22.setty.data

import com.aksworns22.setty.model.ListingNotification
import com.aksworns22.setty.model.notificationStatusOf
import com.aksworns22.setty.network.NotificationResponse
import com.aksworns22.setty.network.SettyNetworkApi
import com.aksworns22.setty.network.apiCall

class NotificationRepository(
    private val settyNetworkApi: SettyNetworkApi,
) {
    suspend fun getNotifications(): List<ListingNotification> =
        apiCall { settyNetworkApi.getNotifications() }.items.map { it.toNotification() }

    suspend fun ack(notificationId: Long) {
        apiCall { settyNetworkApi.ackNotification(notificationId) }
    }
}

private fun NotificationResponse.toNotification() = ListingNotification(
    id = id,
    listingId = listingId,
    title = title ?: "삭제된 매물",
    status = notificationStatusOf(status),
    createdAt = createdAt,
)
