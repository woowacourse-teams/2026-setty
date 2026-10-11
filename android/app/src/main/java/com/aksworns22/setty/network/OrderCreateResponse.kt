package com.aksworns22.setty.network

import kotlinx.serialization.Serializable

@Serializable
data class OrderCreateResponse(
    val id: Long,
    val listingId: Long,
    val buyerId: Long,
    val deliveryStatus: String,
)
