package com.setty.android.data

import com.setty.android.core.model.FurnitureCategory
import com.setty.android.core.model.SaleStatus
import com.setty.android.model.categoryLabel
import com.setty.android.model.isPaymentInProgress
import com.setty.android.model.isPurchasable
import com.setty.android.network.DimensionsResponse
import com.setty.android.network.FurnitureDetailResponse
import com.setty.android.network.FurnitureSummaryResponse
import com.setty.android.network.ListingDongResponse
import com.setty.android.network.ListingImageResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FurnitureDetailMapperTest {

    private val response = FurnitureDetailResponse(
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
        val listing = response.toFurnitureDetail()

        assertEquals(FurnitureCategory.TABLE, listing.category)
        assertEquals("테이블", listing.categoryLabel)
        assertEquals(140, listing.widthCm)
        assertEquals(80, listing.depthCm)
        assertEquals(75, listing.heightCm)
    }

    @Test
    fun `사진을 노출 순서대로 정렬한다`() {
        assertEquals(
            listOf("https://image/first", "https://image/second"),
            response.toFurnitureDetail().imageUrls,
        )
    }

    @Test
    fun `판매중인 매물만 구매할 수 있다`() {
        assertTrue(response.toFurnitureDetail().isPurchasable)
        assertFalse(response.copy(saleStatus = "RESERVED").toFurnitureDetail().isPurchasable)
        assertFalse(response.copy(saleStatus = "SOLD").toFurnitureDetail().isPurchasable)
    }

    @Test
    fun `알 수 없는 판매 상태는 구매할 수 없는 판매완료로 취급한다`() {
        val listing = response.copy(saleStatus = "UNKNOWN").toFurnitureDetail()

        assertEquals(SaleStatus.SOLD, listing.saleStatus)
        assertFalse(listing.isPurchasable)
    }

    @Test
    fun `결제 진행 중인 매물은 판매중이어도 구매할 수 없다`() {
        val listing = response.copy(hasPurchaseRequest = true).toFurnitureDetail()

        assertTrue(listing.isPaymentInProgress)
        assertFalse(listing.isPurchasable)
    }

    @Test
    fun `판매중이 아닌 매물은 결제 진행 중보다 판매 상태를 보여준다`() {
        val listing = response.copy(saleStatus = "SOLD", hasPurchaseRequest = true).toFurnitureDetail()

        assertFalse(listing.isPaymentInProgress)
    }

    @Test
    fun `목록 항목에 결제 진행 중 여부를 담는다`() {
        val summary = FurnitureSummaryResponse(
            id = 1,
            title = "원목 4인 식탁",
            price = 120_000,
            deliveryFee = 20_000,
            totalPrice = 140_000,
            category = "TABLE",
            conditionGrade = "A",
            createdAt = "2026-09-30T00:00:00Z",
        )

        assertTrue(summary.copy(hasPurchaseRequest = true).toFurniture().hasPurchaseRequest)
        assertFalse(summary.toFurniture().hasPurchaseRequest)
    }

    @Test
    fun `판매자 행정동을 구와 동 이름으로 보여준다`() {
        val dong = ListingDongResponse(code = "1144066000", name = "서교동", district = "마포구")
        val summary = FurnitureSummaryResponse(
            id = 1,
            title = "원목 4인 식탁",
            price = 120_000,
            deliveryFee = 20_000,
            totalPrice = 140_000,
            category = "TABLE",
            conditionGrade = "A",
            createdAt = "2026-09-30T00:00:00Z",
            dong = dong,
        )

        assertEquals("마포구 서교동", response.copy(dong = dong).toFurnitureDetail().dongName)
        assertEquals("마포구 서교동", summary.toFurniture().dongName)
    }

    @Test
    fun `행정동 도입 전에 등록한 매물은 행정동이 없다`() {
        assertNull(response.toFurnitureDetail().dongName)
    }
}
