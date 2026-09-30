package com.aksworns22.setty.model

data class MyListing(
    val id: Long,
    val title: String,
    val thumbnailUrl: String?,
    val totalPrice: Int,
    val category: ListingCategory?,
    val conditionGrade: String,
    val saleStatus: SaleStatus,
    val hasPurchaseRequest: Boolean,
    val createdAt: String,
) {
    val categoryLabel: String
        get() = category?.label ?: "기타"

    val registeredDateLabel: String
        get() = formatRegisteredDate(createdAt)
}
