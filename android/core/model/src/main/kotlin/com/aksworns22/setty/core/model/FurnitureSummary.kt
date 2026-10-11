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
)
