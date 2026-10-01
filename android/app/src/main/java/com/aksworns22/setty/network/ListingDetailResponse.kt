package com.aksworns22.setty.network

import kotlinx.serialization.Serializable

@Serializable
data class ListingDetailResponse(
    val id: Long,
    val title: String,
    val description: String,
    val price: Int,
    val deliveryFee: Int,
    val totalPrice: Int,
    val category: String,
    val conditionGrade: String,
    val dimensions: DimensionsResponse,
    val saleStatus: String,
    val images: List<ListingImageResponse>,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class DimensionsResponse(
    val widthCm: Int,
    val depthCm: Int,
    val heightCm: Int,
)

@Serializable
data class ListingImageResponse(
    val id: Long,
    val url: String,
    val displayOrder: Int,
)
