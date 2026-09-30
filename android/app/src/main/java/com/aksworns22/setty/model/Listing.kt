package com.aksworns22.setty.model

import java.text.NumberFormat
import java.util.Locale

enum class ListingCategory(val label: String) {
    SOFA("소파"),
    TABLE("테이블"),
    DESK("책상"),
    CHAIR("의자"),
    STORAGE("수납장"),
    BED("침대"),
    ;

    companion object {
        fun fromOrNull(value: String): ListingCategory? = entries.find { it.name == value }
    }
}

data class Listing(
    val id: Long,
    val title: String,
    val thumbnailUrl: String?,
    val price: Int,
    val deliveryFee: Int,
    val totalPrice: Int,
    val category: ListingCategory?,
    val conditionGrade: String,
    val createdAt: String,
) {
    val categoryLabel: String
        get() = category?.label ?: "기타"

    val registeredDateLabel: String
        get() = formatRegisteredDate(createdAt)
}

fun formatWon(value: Int): String =
    "${NumberFormat.getNumberInstance(Locale.KOREA).format(value)}원"

/** ISO-8601 문자열(예: 2026-09-30T05:03:41Z)에서 "9.30" 형태의 등록일을 만든다. */
fun formatRegisteredDate(createdAt: String): String {
    if (createdAt.length < 10) return ""
    val month = createdAt.substring(5, 7).toIntOrNull()
    val day = createdAt.substring(8, 10).toIntOrNull()
    if (month == null || day == null) return ""
    return "$month.$day"
}
