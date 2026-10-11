package com.aksworns22.setty.data

import com.aksworns22.setty.network.OrderCreateRequest
import com.aksworns22.setty.network.PaymentConfirmRequest
import com.aksworns22.setty.network.PaymentFailRequest
import com.aksworns22.setty.network.SettyNetworkApi
import com.aksworns22.setty.network.toApiException
import retrofit2.HttpException

class PaymentRepository(
    private val settyNetworkApi: SettyNetworkApi,
) {
    /** 결제 전에 PENDING 주문을 만들고 서버 내부 주문 id를 돌려준다. */
    suspend fun createOrder(listingId: Long): Long {
        try {
            return settyNetworkApi.createOrder(OrderCreateRequest(listingId)).id
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
