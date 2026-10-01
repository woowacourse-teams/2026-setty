package com.aksworns22.setty.data

import com.aksworns22.setty.model.ListingCategory
import com.aksworns22.setty.model.SaleStatus
import com.aksworns22.setty.network.MyListingResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MyListingMapperTest {

    private val response = MyListingResponse(
        id = 1,
        title = "원목 4인 식탁",
        thumbnailUrl = "https://image/first",
        price = 120_000,
        deliveryFee = 20_000,
        totalPrice = 140_000,
        category = "TABLE",
        conditionGrade = "A",
        saleStatus = "RESERVED",
        hasPurchaseRequest = true,
        createdAt = "2026-09-30T00:00:00Z",
    )

    @Test
    fun `카테고리와 판매 상태를 내 매물 모델로 변환한다`() {
        val listing = response.toMyListing()

        assertEquals(ListingCategory.TABLE, listing.category)
        assertEquals(SaleStatus.RESERVED, listing.saleStatus)
        assertEquals(140_000, listing.totalPrice)
        assertTrue(listing.hasPurchaseRequest)
        assertEquals("9.30", listing.registeredDateLabel)
    }

    @Test
    fun `알 수 없는 카테고리는 기타로 표시한다`() {
        assertEquals("기타", response.copy(category = "LAMP").toMyListing().categoryLabel)
    }

    @Test
    fun `알 수 없는 판매 상태는 판매완료로 취급한다`() {
        assertEquals(SaleStatus.SOLD, response.copy(saleStatus = "UNKNOWN").toMyListing().saleStatus)
    }
}
