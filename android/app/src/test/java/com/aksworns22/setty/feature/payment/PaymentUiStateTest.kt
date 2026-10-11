package com.aksworns22.setty.feature.payment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentUiStateTest {

    @Test
    fun `결제위젯이 로드되기 전에는 결제할 수 없다`() {
        val uiState = PaymentUiState(isWidgetLoaded = false)

        assertFalse(uiState.canPay)
    }

    @Test
    fun `약관 동의 상태를 아직 모르면 결제할 수 있다`() {
        val uiState = PaymentUiState(isWidgetLoaded = true, isAgreed = null)

        assertTrue(uiState.canPay)
    }

    @Test
    fun `필수 약관에 동의하지 않으면 결제할 수 없다`() {
        val uiState = PaymentUiState(isWidgetLoaded = true, isAgreed = false)

        assertFalse(uiState.canPay)
    }

    @Test
    fun `결제가 진행 중이면 다시 결제할 수 없다`() {
        val uiState = PaymentUiState(
            isWidgetLoaded = true,
            isAgreed = true,
            step = PaymentStep.REQUESTING,
        )

        assertFalse(uiState.canPay)
    }

    @Test
    fun `서버 승인 중에는 화면을 벗어날 수 없다`() {
        assertFalse(PaymentUiState(step = PaymentStep.CONFIRMING).canLeave)
        assertTrue(PaymentUiState(step = PaymentStep.REQUESTING).canLeave)
    }

    @Test
    fun `토스 orderId는 주문 id로 시작하고 매번 다르다`() {
        val first = PaymentViewModel.tossOrderIdOf(101)
        val second = PaymentViewModel.tossOrderIdOf(101)

        assertEquals("101", first.substringBefore('_'))
        assertNotEquals(first, second)
        assertTrue(first.length in 6..64)
    }
}
