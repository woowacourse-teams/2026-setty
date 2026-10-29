package com.setty.android.feature.listingeditor

import com.setty.android.core.model.FurnitureCategory
import com.setty.android.feature.common.LoadUiState
import com.setty.android.model.ListingImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListingEditorUiStateTest {

    private val validState = ListingEditorUiState(
        title = "원목 의자",
        description = "사용감이 적은 원목 의자입니다.",
        price = "120000",
        category = FurnitureCategory.CHAIR,
        conditionGrade = "A",
        widthCm = "100",
        depthCm = "50",
        heightCm = "40",
        retainedImages = listOf(ListingImage(id = 1, url = "https://image/1")),
    )

    @Test
    fun `모든 입력이 유효하고 사진이 있으면 저장할 수 있다`() {
        assertTrue(validState.canSubmit)
    }

    @Test
    fun `사진이 없으면 저장할 수 없다`() {
        assertFalse(validState.copy(retainedImages = emptyList()).canSubmit)
    }

    @Test
    fun `가격이 1억 원을 넘으면 오류다`() {
        val state = validState.copy(price = "100000001")

        assertTrue(state.priceError)
        assertFalse(state.canSubmit)
    }

    @Test
    fun `가격 0원은 허용한다`() {
        assertTrue(validState.copy(price = "0").canSubmit)
    }

    @Test
    fun `크기는 1~1000cm 사이여야 한다`() {
        assertTrue(validState.copy(widthCm = "0").widthError)
        assertTrue(validState.copy(depthCm = "1001").depthError)
        assertFalse(validState.copy(heightCm = "1000").heightError)
    }

    @Test
    fun `카테고리나 등급을 고르지 않으면 저장할 수 없다`() {
        assertFalse(validState.copy(category = null).canSubmit)
        assertFalse(validState.copy(conditionGrade = null).canSubmit)
    }

    @Test
    fun `공백만 있는 제목과 설명으로는 저장할 수 없다`() {
        assertFalse(validState.copy(title = "   ").canSubmit)
        assertFalse(validState.copy(description = "  ").canSubmit)
    }

    @Test
    fun `수정할 매물을 불러오는 중에는 저장할 수 없다`() {
        assertFalse(validState.copy(loadState = LoadUiState.Loading).canSubmit)
    }

    @Test
    fun `남은 사진 자리는 최대 5장 기준으로 계산한다`() {
        val images = (1L..5L).map { ListingImage(id = it, url = "https://image/$it") }

        assertEquals(4, validState.remainingImageSlots)
        assertEquals(0, validState.copy(retainedImages = images).remainingImageSlots)
    }

    @Test
    fun `입력값을 앞뒤 공백 없이 서버 요청 값으로 변환한다`() {
        val draft = validState.copy(title = "  원목 의자 ").toDraft()

        assertEquals("원목 의자", draft.title)
        assertEquals(120000, draft.price)
        assertEquals(100, draft.widthCm)
    }
}
