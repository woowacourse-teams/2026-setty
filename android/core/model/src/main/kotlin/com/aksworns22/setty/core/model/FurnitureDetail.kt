package com.aksworns22.setty.core.model

data class FurnitureDetail(
    val id: Long,
    val title: String,
    val description: String,
    val price: Int,
    val deliveryFee: Int,
    val totalPrice: Int,
    val category: FurnitureCategory,
    val conditionGrade: String,
    val widthCm: Int,
    val depthCm: Int,
    val heightCm: Int,
    val saleStatus: SaleStatus,
    val imageUrls: List<String>,
)
