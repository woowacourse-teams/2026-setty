package com.aksworns22.setty.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ListingTest {
    @Test
    fun `금액을 천 단위 구분 기호와 원 단위로 표시한다`() {
        assertEquals("150,000원", formatWon(150000))
        assertEquals("0원", formatWon(0))
    }

    @Test
    fun `등록 일시를 월과 일로 표시한다`() {
        assertEquals("9.30", formatRegisteredDate("2026-09-30T05:03:41Z"))
        assertEquals("1.5", formatRegisteredDate("2026-01-05T00:00:00.123456Z"))
    }

    @Test
    fun `형식이 맞지 않는 등록 일시는 빈 문자열로 표시한다`() {
        assertEquals("", formatRegisteredDate(""))
        assertEquals("", formatRegisteredDate("invalid-date"))
    }

    @Test
    fun `알 수 없는 카테고리는 null로 변환한다`() {
        assertEquals(ListingCategory.SOFA, ListingCategory.fromOrNull("SOFA"))
        assertNull(ListingCategory.fromOrNull("LAMP"))
    }
}
