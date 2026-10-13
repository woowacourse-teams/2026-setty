package com.aksworns22.setty.network

import kotlinx.serialization.Serializable

@Serializable
data class ListingListResponse(
    val items: List<FurnitureSummaryResponse>
)

@Serializable
data class FurnitureSummaryResponse(
    val id: Long,
    val title: String,
    val thumbnailUrl: String? = null,
    val price: Int,
    val deliveryFee: Int,
    val totalPrice: Int,
    val category: String,
    val conditionGrade: String,
    val createdAt: String,
    // 행정동 도입 전에 등록한 매물은 null
    val dong: ListingDongResponse? = null,
)

@Serializable
data class ListingDongResponse(
    val code: String,
    val name: String,
    val district: String,
)
