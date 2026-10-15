package com.aksworns22.setty.network

import kotlinx.serialization.Serializable

@Serializable
data class NotificationListResponse(
    val items: List<NotificationResponse>,
)

@Serializable
data class NotificationResponse(
    val id: Long,
    val listingId: Long,
    val title: String? = null,
    val status: String,
    val createdAt: String,
)
