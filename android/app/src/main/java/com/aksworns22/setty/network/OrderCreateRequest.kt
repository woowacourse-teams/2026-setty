package com.aksworns22.setty.network

import kotlinx.serialization.Serializable

@Serializable
data class OrderCreateRequest(
    val listingId: Long,
)
