package com.aksworns22.setty.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test

class DeliveryFeeBudgetTest {

    @Test
    fun `슬라이더 값을 5천 원 단위 예산으로 맞춘다`() {
        assertEquals(25_000, DeliveryFeeBudget.snap(24_000f))
        assertEquals(20_000, DeliveryFeeBudget.snap(22_400f))
    }

    @Test
    fun `슬라이더 범위를 벗어난 값은 양 끝으로 맞춘다`() {
        assertEquals(10_000, DeliveryFeeBudget.snap(0f))
        assertEquals(100_000, DeliveryFeeBudget.snap(200_000f))
    }

    @Test
    fun `만 원 단위로 예산을 보여준다`() {
        assertEquals("2만 원", DeliveryFeeBudget.format(20_000))
        assertEquals("1.5만 원", DeliveryFeeBudget.format(15_000))
        assertEquals("23,500원", DeliveryFeeBudget.format(23_500))
    }
}
