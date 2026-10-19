package cloud.setty.setty.model

import cloud.setty.setty.core.model.FurnitureCategory
import cloud.setty.setty.core.model.FurnitureDetail
import cloud.setty.setty.core.model.FurnitureSummary
import cloud.setty.setty.core.model.SaleStatus
import java.text.NumberFormat
import java.util.Locale

val FurnitureCategory.label: String
    get() = when (this) {
        FurnitureCategory.SOFA -> "소파"
        FurnitureCategory.TABLE -> "테이블"
        FurnitureCategory.DESK -> "책상"
        FurnitureCategory.CHAIR -> "의자"
        FurnitureCategory.STORAGE -> "수납장"
        FurnitureCategory.BED -> "침대"
        FurnitureCategory.ETC -> "기타"
    }

/** 알 수 없는 카테고리는 [FurnitureCategory.ETC]로 변환한다. */
fun furnitureCategoryOf(value: String): FurnitureCategory =
    FurnitureCategory.entries.find { it.name == value } ?: FurnitureCategory.ETC

val SaleStatus.label: String
    get() = when (this) {
        SaleStatus.AVAILABLE -> "판매중"
        SaleStatus.RESERVED -> "예약중"
        SaleStatus.SOLD -> "판매완료"
    }

fun saleStatusOrNull(value: String): SaleStatus? = SaleStatus.entries.find { it.name == value }

val FurnitureSummary.categoryLabel: String
    get() = category.label

val FurnitureSummary.registeredDateLabel: String
    get() = formatRegisteredDate(createdAt)

val FurnitureDetail.categoryLabel: String
    get() = category.label

/** 판매중이지만 결제 대기 주문이 선점한 매물인지. 판매중이 아니면 판매 상태를 우선한다. */
val FurnitureDetail.isPaymentInProgress: Boolean
    get() = saleStatus == SaleStatus.AVAILABLE && hasPurchaseRequest

val FurnitureDetail.isPurchasable: Boolean
    get() = saleStatus == SaleStatus.AVAILABLE && !hasPurchaseRequest

val MyFurniture.categoryLabel: String
    get() = category.label

val MyFurniture.registeredDateLabel: String
    get() = formatRegisteredDate(createdAt)

/** 서버가 받는 상품 상태 등급. 좋은 등급부터 나열한다. */
val ConditionGrades = listOf("S", "A", "B", "C")

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
