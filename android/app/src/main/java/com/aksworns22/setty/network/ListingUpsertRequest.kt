package com.aksworns22.setty.network

import kotlinx.serialization.Serializable

@Serializable
data class ListingCreateRequest(
    val title: String,
    val description: String,
    val price: Int,
    val category: String,
    val conditionGrade: String,
    val dimensions: DimensionsRequest,
)

@Serializable
data class ListingUpdateRequest(
    val title: String,
    val description: String,
    val price: Int,
    val category: String,
    val conditionGrade: String,
    val dimensions: DimensionsRequest,
    // 유지할 기존 이미지 id. 이 순서대로 재정렬되고 새 이미지는 뒤에 붙는다
    val retainedImageIds: List<Long>,
)

@Serializable
data class DimensionsRequest(
    val widthCm: Int,
    val depthCm: Int,
    val heightCm: Int,
)
