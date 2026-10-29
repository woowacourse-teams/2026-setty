package com.setty.android.network

import kotlinx.serialization.Serializable

@Serializable
data class OrderCreateRequest(
    val listingId: Long,
)
