package com.aksworns22.setty.feature.sellerpage

import com.aksworns22.setty.model.ListingCategory
import com.aksworns22.setty.model.MyListing
import com.aksworns22.setty.model.SaleStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class SellerPageUiStateTest {

    @Test
    fun `판매 상태별 매물 수를 센다`() {
        val uiState = SellerPageUiState(
            listings = listOf(
                listing(id = 1, saleStatus = SaleStatus.AVAILABLE),
                listing(id = 2, saleStatus = SaleStatus.AVAILABLE),
                listing(id = 3, saleStatus = SaleStatus.SOLD),
            ),
        )

        assertEquals(2, uiState.countOf(SaleStatus.AVAILABLE))
        assertEquals(0, uiState.countOf(SaleStatus.RESERVED))
        assertEquals(1, uiState.countOf(SaleStatus.SOLD))
    }

    private fun listing(id: Long, saleStatus: SaleStatus) = MyListing(
        id = id,
        title = "가구 $id",
        thumbnailUrl = null,
        totalPrice = 15000,
        category = ListingCategory.SOFA,
        conditionGrade = "A",
        saleStatus = saleStatus,
        hasPurchaseRequest = false,
        createdAt = "2026-09-30T05:03:41Z",
    )
}
