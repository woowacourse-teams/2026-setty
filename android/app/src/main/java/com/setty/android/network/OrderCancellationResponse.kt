package com.setty.android.network

import kotlinx.serialization.Serializable

@Serializable
data class OrderCancellationResponse(
    val orderId: Long,
    val orderStatus: String,
    val message: String,
)
