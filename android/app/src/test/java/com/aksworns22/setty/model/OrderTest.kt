package com.aksworns22.setty.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderTest {

    private val order = Order(
        id = 100,
        listing = OrderedFurniture(id = 1, name = "원목 의자", thumbnailUrl = null, price = 120000, deliveryFee = 10000),
        orderStatus = OrderStatus.CONFIRMED,
        deliveryStatus = DeliveryStatus.REQUESTED,
    )

    @Test
    fun `결제가 끝난 주문만 취소할 수 있다`() {
        assertTrue(order.canCancel)
        assertFalse(order.copy(orderStatus = OrderStatus.PENDING).canCancel)
        assertFalse(order.copy(orderStatus = OrderStatus.CANCEL_PENDING).canCancel)
        assertFalse(order.copy(orderStatus = OrderStatus.CANCELLED).canCancel)
    }

    @Test
    fun `주문 상태를 모르면 배송 요청 단계에서만 취소할 수 있다`() {
        assertTrue(order.copy(orderStatus = null).canCancel)
        assertFalse(order.copy(orderStatus = null, deliveryStatus = DeliveryStatus.ACCEPTED).canCancel)
    }

    @Test
    fun `취소 흐름의 주문은 주문 상태를, 아니면 배송 상태를 보여준다`() {
        assertEquals("취소 대기", order.copy(orderStatus = OrderStatus.CANCEL_PENDING).statusLabel)
        assertEquals("배차 완료", order.copy(deliveryStatus = DeliveryStatus.ACCEPTED).statusLabel)
    }

    @Test
    fun `배송 진행률은 배송 완료에서 1이다`() {
        assertEquals(0.25f, DeliveryStatus.REQUESTED.progress)
        assertEquals(1f, DeliveryStatus.DELIVERED.progress)
        assertEquals(0f, DeliveryStatus.PENDING.progress)
    }

    @Test
    fun `총 결제 금액은 가격과 배송비의 합이다`() {
        assertEquals(130000, order.listing?.totalPrice)
    }

    @Test
    fun `알 수 없는 상태 문자열을 변환한다`() {
        assertEquals(null, orderStatusOrNull("UNKNOWN"))
        assertEquals(null, orderStatusOrNull(null))
        assertEquals(DeliveryStatus.PENDING, deliveryStatusOf("UNKNOWN"))
    }
}
