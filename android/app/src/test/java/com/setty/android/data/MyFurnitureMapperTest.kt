package com.setty.android.data

import com.setty.android.core.model.FurnitureCategory
import com.setty.android.core.model.SaleStatus
import com.setty.android.model.categoryLabel
import com.setty.android.model.registeredDateLabel
import com.setty.android.network.MyFurnitureResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MyFurnitureMapperTest {

    private val response = MyFurnitureResponse(
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
        canUpdate = false,
        canDelete = false,
        createdAt = "2026-09-30T00:00:00Z",
    )

    @Test
    fun `카테고리와 판매 상태를 내 매물 모델로 변환한다`() {
        val furniture = response.toMyFurniture()

        assertEquals(FurnitureCategory.TABLE, furniture.category)
        assertEquals(SaleStatus.RESERVED, furniture.saleStatus)
        assertEquals(140_000, furniture.totalPrice)
        assertTrue(furniture.hasPurchaseRequest)
        assertFalse(furniture.canUpdate)
        assertEquals("9.30", furniture.registeredDateLabel)
    }

    @Test
    fun `알 수 없는 카테고리는 기타로 표시한다`() {
        assertEquals("기타", response.copy(category = "LAMP").toMyFurniture().categoryLabel)
    }

    @Test
    fun `알 수 없는 판매 상태는 판매완료로 취급한다`() {
        assertEquals(SaleStatus.SOLD, response.copy(saleStatus = "UNKNOWN").toMyFurniture().saleStatus)
    }
}
