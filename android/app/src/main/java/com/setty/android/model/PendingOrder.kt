package com.setty.android.model

/** 결제 전에 만든 PENDING 주문. [totalPrice]는 서버가 결제 승인 때 검증하는 금액이다. */
data class PendingOrder(
    val id: Long,
    val totalPrice: Int,
)
