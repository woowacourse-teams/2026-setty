package cloud.setty.setty.data

import cloud.setty.setty.model.ListingNotification
import cloud.setty.setty.model.notificationStatusOf
import cloud.setty.setty.network.NotificationResponse
import cloud.setty.setty.network.SettyNetworkApi
import cloud.setty.setty.network.apiCall

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
