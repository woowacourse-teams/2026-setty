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
    // 판매자 행정동 표시 이름(예: "마포구 서교동"). 행정동 도입 전에 등록한 매물은 null
    val dongName: String? = null,
    // 결제 대기 주문이 매물을 선점하고 있으면 true. 선점은 판매 상태를 바꾸지 않는다
    val hasPurchaseRequest: Boolean = false,
)
