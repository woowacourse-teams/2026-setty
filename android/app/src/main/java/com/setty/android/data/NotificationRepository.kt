package com.setty.android.data

import com.setty.android.model.ListingNotification
import com.setty.android.model.notificationStatusOf
import com.setty.android.network.NotificationResponse
import com.setty.android.network.SettyNetworkApi
import com.setty.android.network.apiCall

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
