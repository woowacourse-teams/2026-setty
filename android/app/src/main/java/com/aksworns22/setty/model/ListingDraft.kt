package com.aksworns22.setty.model

import com.aksworns22.setty.core.model.FurnitureCategory

/** 매물 등록·수정 화면에서 서버로 보낼 매물 정보. */
data class ListingDraft(
    val title: String,
    val description: String,
    val price: Int,
    val category: FurnitureCategory,
    val conditionGrade: String,
    val widthCm: Int,
    val depthCm: Int,
    val heightCm: Int,
)

/** 수정 화면에서 쓰는 기존 매물. 이미지를 유지·삭제하려면 이미지 id가 필요하다. */
data class EditableListing(
    val id: Long,
    val draft: ListingDraft,
    val images: List<ListingImage>,
)

data class ListingImage(
    val id: Long,
    val url: String,
)
