package com.aksworns22.setty.network

import kotlinx.serialization.Serializable

@Serializable
data class OrderResponse(
    val id: Long,
    // 매물 레코드가 없으면 null
    val listing: OrderListingResponse? = null,
    // 서버가 내려주지 않는 버전도 있어 없으면 null
    val orderStatus: String? = null,
    val deliveryStatus: String,
)

@Serializable
data class OrderListingResponse(
    val id: Long,
    val name: String,
    val thumbnailUrl: String? = null,
    val price: Int,
    val deliveryFee: Int,
)
