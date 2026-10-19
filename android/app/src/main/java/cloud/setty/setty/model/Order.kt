package cloud.setty.setty.model

data class Order(
    val id: Long,
    // 매물 레코드가 없으면 null
    val listing: OrderedFurniture?,
    // 서버가 주문 상태를 내려주지 않으면 null
    val orderStatus: OrderStatus?,
    val deliveryStatus: DeliveryStatus,
) {
    /** 결제가 끝난 주문만 취소를 요청할 수 있다. 주문 상태를 모르면 배송 요청 단계에서만 허용한다. */
    val canCancel: Boolean
        get() = when (orderStatus) {
            null -> deliveryStatus == DeliveryStatus.REQUESTED
            else -> orderStatus == OrderStatus.CONFIRMED
        }
}

data class OrderedFurniture(
    val id: Long,
    val name: String,
    val thumbnailUrl: String?,
    val price: Int,
    val deliveryFee: Int,
) {
    val totalPrice: Int
        get() = price + deliveryFee
}

enum class OrderStatus {
    PENDING,
    CONFIRMED,
    CANCEL_PENDING,
    CANCELLED,
    EXPIRED,
}

enum class DeliveryStatus {
    PENDING,
    REQUESTED,
    ACCEPTED,
    PICKED_UP,
    DELIVERED,
}
