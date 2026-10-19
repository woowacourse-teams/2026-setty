package cloud.setty.setty.network

import kotlinx.serialization.Serializable

@Serializable
data class PaymentConfirmResponse(
    // 서버 내부 주문 id
    val orderId: Long,
    val status: String,
    val amount: Int,
)
