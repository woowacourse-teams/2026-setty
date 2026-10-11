package com.aksworns22.setty.feature.home

import com.aksworns22.setty.core.model.FurnitureCategory
import com.aksworns22.setty.core.model.FurnitureSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeUiStateTest {
    private val sofa = listing(id = 1, category = FurnitureCategory.SOFA)
    private val table = listing(id = 2, category = FurnitureCategory.TABLE)

    @Test
    fun `선택한 카테고리가 없으면 모든 가구를 보여준다`() {
        val uiState = HomeUiState(furnitures = listOf(sofa, table))

        assertEquals(listOf(sofa, table), uiState.visibleFurnitures)
    }

    @Test
    fun `선택한 카테고리의 가구만 보여준다`() {
        val uiState = HomeUiState(
            furnitures = listOf(sofa, table),
            selectedCategory = FurnitureCategory.TABLE,
        )

        assertEquals(listOf(table), uiState.visibleFurnitures)
    }

    @Test
    fun `제목에 검색어가 들어간 가구를 대소문자 구분 없이 찾는다`() {
        val chair = listing(id = 3, category = FurnitureCategory.CHAIR).copy(title = "IKEA 원목 의자")
        val uiState = HomeUiState(furnitures = listOf(sofa, chair))

        assertEquals(listOf(chair), uiState.search(" ikea "))
        assertEquals(listOf(chair), uiState.search("의자"))
    }

    @Test
    fun `검색어가 비어 있으면 결과가 없다`() {
        assertEquals(emptyList<FurnitureSummary>(), HomeUiState(furnitures = listOf(sofa)).search("  "))
    }

    private fun listing(id: Long, category: FurnitureCategory) = FurnitureSummary(
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
