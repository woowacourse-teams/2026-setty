package com.aksworns22.setty.core.model

data class FurnitureSummary(
    val id: Long,
    val title: String,
    val thumbnailUrl: String?,
    val price: Int,
    val deliveryFee: Int,
    val totalPrice: Int,
    val category: FurnitureCategory,
    val conditionGrade: String,
    val createdAt: String,
    // 판매자 행정동 표시 이름(예: "마포구 서교동"). 행정동 도입 전에 등록한 매물은 null
    val dongName: String? = null,
)
