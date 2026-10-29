package com.setty.android.data

import com.setty.android.model.PendingOrder
import com.setty.android.network.OrderCreateRequest
import com.setty.android.network.PaymentConfirmRequest
import com.setty.android.network.PaymentFailRequest
import com.setty.android.network.SettyNetworkApi
import com.setty.android.network.toApiException
import retrofit2.HttpException

class PaymentRepository(
    private val settyNetworkApi: SettyNetworkApi,
) {
    /** 결제 전에 PENDING 주문을 만들고 서버 내부 주문 id와 결제할 총액을 돌려준다. */
    suspend fun createOrder(listingId: Long): PendingOrder {
        try {
            val response = settyNetworkApi.createOrder(OrderCreateRequest(listingId))
            return PendingOrder(id = response.id, totalPrice = response.totalPrice)
        } catch (e: HttpException) {
            throw e.toApiException()
        }
    }

    /** 토스 결제 성공 결과로 서버 승인을 요청한다. 이미 승인된 주문이면 서버가 기존 결과를 돌려준다(멱등). */
    suspend fun confirm(paymentKey: String, tossOrderId: String, amount: Int) {
        try {
            settyNetworkApi.confirmPayment(PaymentConfirmRequest(paymentKey, tossOrderId, amount))
        } catch (e: HttpException) {
            throw e.toApiException()
        }
    }

    /** 결제 실패·취소를 알려 PENDING 주문을 정리(매물 선점 해제)한다. */
    suspend fun fail(tossOrderId: String) {
        try {
            settyNetworkApi.failPayment(PaymentFailRequest(tossOrderId))
        } catch (e: HttpException) {
            throw e.toApiException()
        }
    }
}
