package com.aksworns22.setty.data

import com.aksworns22.setty.model.ListingCategory
import com.aksworns22.setty.model.SaleStatus
import com.aksworns22.setty.network.DimensionsResponse
import com.aksworns22.setty.network.ListingDetailResponse
import com.aksworns22.setty.network.ListingImageResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListingDetailMapperTest {

    private val response = ListingDetailResponse(
        id = 1,
        title = "원목 4인 식탁",
        description = "깨끗하게 사용했습니다",
        price = 120_000,
        deliveryFee = 20_000,
        totalPrice = 140_000,
        category = "TABLE",
        conditionGrade = "A",
        dimensions = DimensionsResponse(widthCm = 140, depthCm = 80, heightCm = 75),
        saleStatus = "AVAILABLE",
        images = listOf(
            ListingImageResponse(id = 2, url = "https://image/second", displayOrder = 1),
            ListingImageResponse(id = 1, url = "https://image/first", displayOrder = 0),
        ),
        createdAt = "2026-09-30T00:00:00Z",
        updatedAt = "2026-09-30T00:00:00Z",
    )

    @Test
    fun `카테고리와 크기를 매물 상세 모델로 변환한다`() {
        val listing = response.toListingDetail()

        assertEquals(ListingCategory.TABLE, listing.category)
        assertEquals("테이블", listing.categoryLabel)
        assertEquals(140, listing.widthCm)
        assertEquals(80, listing.depthCm)
        assertEquals(75, listing.heightCm)
    }

    @Test
    fun `사진을 노출 순서대로 정렬한다`() {
        assertEquals(
            listOf("https://image/first", "https://image/second"),
            response.toListingDetail().imageUrls,
        )
    }

    @Test
    fun `판매중인 매물만 구매할 수 있다`() {
        assertTrue(response.toListingDetail().isPurchasable)
        assertFalse(response.copy(saleStatus = "RESERVED").toListingDetail().isPurchasable)
        assertFalse(response.copy(saleStatus = "SOLD").toListingDetail().isPurchasable)
    }

    @Test
    fun `알 수 없는 판매 상태는 구매할 수 없는 판매완료로 취급한다`() {
        val listing = response.copy(saleStatus = "UNKNOWN").toListingDetail()

        assertEquals(SaleStatus.SOLD, listing.saleStatus)
        assertFalse(listing.isPurchasable)
    }
}
