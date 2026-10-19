package cloud.setty.setty.model

import cloud.setty.setty.core.model.FurnitureCategory
import cloud.setty.setty.core.model.SaleStatus

/** 내가 등록한 매물. 판매 상태와 수정·삭제 가능 여부를 함께 가진다. */
data class MyFurniture(
    val id: Long,
    val title: String,
    val thumbnailUrl: String?,
    val price: Int,
    val deliveryFee: Int,
    val totalPrice: Int,
    val category: FurnitureCategory,
    val conditionGrade: String,
    val saleStatus: SaleStatus,
    val hasPurchaseRequest: Boolean,
    val canUpdate: Boolean,
    val canDelete: Boolean,
    val createdAt: String,
)
