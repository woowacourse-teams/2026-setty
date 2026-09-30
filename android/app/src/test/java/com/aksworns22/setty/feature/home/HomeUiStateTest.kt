package com.aksworns22.setty.feature.home

import com.aksworns22.setty.model.Listing
import com.aksworns22.setty.model.ListingCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeUiStateTest {
    private val sofa = listing(id = 1, category = ListingCategory.SOFA)
    private val table = listing(id = 2, category = ListingCategory.TABLE)

    @Test
    fun `선택한 카테고리가 없으면 모든 가구를 보여준다`() {
        val uiState = HomeUiState(listings = listOf(sofa, table))

        assertEquals(listOf(sofa, table), uiState.visibleListings)
    }

    @Test
    fun `선택한 카테고리의 가구만 보여준다`() {
        val uiState = HomeUiState(
            listings = listOf(sofa, table),
            selectedCategory = ListingCategory.TABLE,
        )

        assertEquals(listOf(table), uiState.visibleListings)
    }

    private fun listing(id: Long, category: ListingCategory) = Listing(
        id = id,
        title = "가구 $id",
        thumbnailUrl = null,
        price = 10000,
        deliveryFee = 5000,
        totalPrice = 15000,
        category = category,
        conditionGrade = "A",
        createdAt = "2026-09-30T05:03:41Z",
    )
}
