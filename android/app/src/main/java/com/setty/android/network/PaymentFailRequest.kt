package com.setty.android.network

import kotlinx.serialization.Serializable

@Serializable
data class PaymentFailRequest(
    // 토스에 넘긴 `<주문id>_<랜덤>` 형태의 orderId
    val orderId: String,
)
