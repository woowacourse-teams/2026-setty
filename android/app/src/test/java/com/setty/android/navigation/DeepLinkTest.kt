package com.setty.android.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinkTest {

    @Test
    fun `매물 링크는 매물 상세로 바꾼다`() {
        assertEquals(DeepLink.Listing(12), parseDeepLink("setty://listings/12"))
    }

    @Test
    fun `주문 링크는 주문 상세로 바꾼다`() {
        assertEquals(DeepLink.Order(7), parseDeepLink("setty://orders/7"))
    }

    @Test
    fun `쿼리와 끝 슬래시는 무시한다`() {
        assertEquals(DeepLink.Listing(3), parseDeepLink("setty://listings/3/?from=push"))
    }

    @Test
    fun `링크가 없으면 진입하지 않는다`() {
        assertNull(parseDeepLink(null))
        assertNull(parseDeepLink(""))
    }

    @Test
    fun `id가 숫자가 아니거나 양수가 아니면 잘못된 링크다`() {
        listOf(
            "setty://listings/abc",
            "setty://listings/0",
            "setty://listings/-1",
            "setty://listings/99999999999999999999",
            "setty://listings/",
        ).forEach { assertEquals(it, DeepLink.Invalid, parseDeepLink(it)) }
    }

    @Test
    fun `모르는 대상이나 다른 스킴이면 잘못된 링크다`() {
        listOf(
            "setty://unknown/1",
            "https://listings/1",
            "setty://listings/1/edit",
            "setty://listings 1",
        ).forEach { assertEquals(it, DeepLink.Invalid, parseDeepLink(it)) }
    }

    @Test
    fun `앱이 꺼져 있을 때는 앱 안 경로대로 스택을 쌓는다`() {
        assertEquals(
            listOf(SettyScreen.Home, SettyScreen.FurnitureDetail(1)),
            DeepLink.Listing(1).toBackStack(),
        )
        assertEquals(
            listOf(SettyScreen.Home, SettyScreen.Orders, SettyScreen.OrderDetail(2)),
            DeepLink.Order(2).toBackStack(),
        )
    }

    @Test
    fun `만든 링크는 다시 같은 대상으로 해석된다`() {
        assertEquals(DeepLink.Listing(5), parseDeepLink(DeepLink.listingUri(5)))
        assertEquals(DeepLink.Order(9), parseDeepLink(DeepLink.orderUri(9)))
    }
}
