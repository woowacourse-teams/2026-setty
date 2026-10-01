package com.aksworns22.setty.network

import kotlinx.serialization.Serializable

@Serializable
data class MyListingListResponse(
    val items: List<MyListingResponse>
)

@Serializable
data class MyListingResponse(
    val id: Long,
    val title: String,
    val thumbnailUrl: String? = null,
    val price: Int,
    val deliveryFee: Int,
    val totalPrice: Int,
    val category: String,
    val conditionGrade: String,
    val saleStatus: String,
    val hasPurchaseRequest: Boolean,
    val createdAt: String,
)
