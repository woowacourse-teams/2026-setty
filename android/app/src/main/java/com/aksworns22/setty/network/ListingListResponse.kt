package com.aksworns22.setty.network

import kotlinx.serialization.Serializable

@Serializable
data class ListingListResponse(
    val items: List<ListingSummaryResponse>
)

@Serializable
data class ListingSummaryResponse(
    val id: Long,
    val title: String,
    val thumbnailUrl: String? = null,
    val price: Int,
    val deliveryFee: Int,
    val totalPrice: Int,
    val category: String,
    val conditionGrade: String,
    val createdAt: String,
)
