package com.setty.android.network

import kotlinx.serialization.Serializable

@Serializable
data class OrderCreateResponse(
    val id: Long,
    val listingId: Long,
    val buyerId: Long,
    val deliveryStatus: String,
    val deliveryFee: Int,
    // 매물 가격 + 주문에 저장한 배송비. 서버가 결제 승인 때 검증하는 금액이다
    val totalPrice: Int,
)
