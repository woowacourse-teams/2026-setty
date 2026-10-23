package com.setty.android.network

import kotlinx.serialization.Serializable

@Serializable
data class ListingListResponse(
    val items: List<FurnitureSummaryResponse>,
    // 다음 페이지 커서. 마지막 페이지거나 페이지로 요청하지 않았으면 없다
    val nextCursor: String? = null,
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
    val hasPurchaseRequest: Boolean = false,
)

@Serializable
data class ListingDongResponse(
    val code: String,
    val name: String,
    val district: String,
)
