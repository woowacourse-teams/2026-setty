package cloud.setty.setty.data

import cloud.setty.setty.model.DeliveryStatus
import cloud.setty.setty.model.OrderStatus
import cloud.setty.setty.network.OrderListingResponse
import cloud.setty.setty.network.OrderResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OrderMapperTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `주문 응답을 주문으로 변환한다`() {
        val order = OrderResponse(
            id = 100,
            listing = OrderListingResponse(1, "원목 의자", null, 120000, 10000),
            orderStatus = "CONFIRMED",
            deliveryStatus = "ACCEPTED",
        ).toOrder()

        assertEquals(100L, order.id)
        assertEquals("원목 의자", order.listing?.name)
        assertEquals(OrderStatus.CONFIRMED, order.orderStatus)
        assertEquals(DeliveryStatus.ACCEPTED, order.deliveryStatus)
    }

    @Test
    fun `주문 상태와 매물이 없는 응답도 읽는다`() {
        val response = json.decodeFromString<OrderResponse>(
            """{"id":100,"listing":null,"deliveryStatus":"REQUESTED"}"""
        )

        val order = response.toOrder()

        assertNull(order.listing)
        assertNull(order.orderStatus)
        assertEquals(DeliveryStatus.REQUESTED, order.deliveryStatus)
    }
}
