package com.aksworns22.setty.model

val OrderStatus.label: String
    get() = when (this) {
        OrderStatus.PENDING -> "결제 대기"
        OrderStatus.CONFIRMED -> "주문 완료"
        OrderStatus.CANCEL_PENDING -> "취소 대기"
        OrderStatus.CANCELLED -> "주문 취소"
        OrderStatus.EXPIRED -> "결제 만료"
    }

val DeliveryStatus.label: String
    get() = when (this) {
        DeliveryStatus.PENDING -> "결제 대기"
        DeliveryStatus.REQUESTED -> "배송 요청"
        DeliveryStatus.ACCEPTED -> "배차 완료"
        DeliveryStatus.PICKED_UP -> "배송 중"
        DeliveryStatus.DELIVERED -> "배송 완료"
    }

/** 주문 목록·상세에서 대표로 보여줄 상태. 취소·만료 흐름이면 주문 상태를, 아니면 배송 상태를 보여준다. */
val Order.statusLabel: String
    get() = when (orderStatus) {
        OrderStatus.CANCEL_PENDING, OrderStatus.CANCELLED, OrderStatus.EXPIRED, OrderStatus.PENDING ->
            orderStatus.label

        else -> deliveryStatus.label
    }

/** 주문 상태 문자열을 변환한다. 알 수 없는 값이면 null. */
fun orderStatusOrNull(value: String?): OrderStatus? = OrderStatus.entries.find { it.name == value }

/** 알 수 없는 배송 상태는 결제 대기로 본다. */
fun deliveryStatusOf(value: String): DeliveryStatus =
    DeliveryStatus.entries.find { it.name == value } ?: DeliveryStatus.PENDING

/** 취소·만료처럼 배송 흐름에서 벗어난 주문인지 여부. */
val Order.isCancelFlow: Boolean
    get() = orderStatus == OrderStatus.CANCEL_PENDING ||
            orderStatus == OrderStatus.CANCELLED ||
            orderStatus == OrderStatus.EXPIRED

val Order.statusDescription: String
    get() = when (orderStatus) {
        OrderStatus.PENDING -> "결제가 완료되지 않은 주문이에요"
        OrderStatus.CANCEL_PENDING -> "취소 요청을 처리하고 있어요"
        OrderStatus.CANCELLED -> "주문이 취소됐어요"
        OrderStatus.EXPIRED -> "결제 시간이 지나 주문이 만료됐어요"
        else -> when (deliveryStatus) {
            DeliveryStatus.PENDING -> "결제가 완료되지 않은 주문이에요"
            DeliveryStatus.REQUESTED -> "배송 기사를 찾고 있어요"
            DeliveryStatus.ACCEPTED -> "배송 기사가 배정됐어요"
            DeliveryStatus.PICKED_UP -> "가구를 싣고 배송 중이에요"
            DeliveryStatus.DELIVERED -> "배송이 완료됐어요"
        }
    }

/** 배송 요청부터 배송 완료까지의 단계. 진행 표시에 쓴다. */
val DeliverySteps = listOf(
    DeliveryStatus.REQUESTED,
    DeliveryStatus.ACCEPTED,
    DeliveryStatus.PICKED_UP,
    DeliveryStatus.DELIVERED,
)

/** 배송 진행률. 결제 전이면 0, 배송 완료면 1. */
val DeliveryStatus.progress: Float
    get() = (DeliverySteps.indexOf(this) + 1).toFloat() / DeliverySteps.size
