package com.aksworns22.setty.model

enum class SaleStatus(val label: String) {
    AVAILABLE("판매중"),
    RESERVED("예약중"),
    SOLD("판매완료"),
    ;

    companion object {
        fun fromOrNull(value: String): SaleStatus? = entries.find { it.name == value }
    }
}

data class ListingDetail(
    val id: Long,
    val title: String,
    val description: String,
    val price: Int,
    val deliveryFee: Int,
    val totalPrice: Int,
    val category: ListingCategory?,
    val conditionGrade: String,
    val widthCm: Int,
    val depthCm: Int,
    val heightCm: Int,
    val saleStatus: SaleStatus,
    val imageUrls: List<String>,
) {
    val categoryLabel: String
        get() = category?.label ?: "기타"

    val isPurchasable: Boolean
        get() = saleStatus == SaleStatus.AVAILABLE
}
