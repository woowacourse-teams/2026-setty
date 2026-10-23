package com.setty.android.data

import com.setty.android.model.Order
import com.setty.android.model.OrderStatus
import com.setty.android.model.OrderedFurniture
import com.setty.android.model.deliveryStatusOf
import com.setty.android.model.orderStatusOrNull
import com.setty.android.network.OrderResponse
import com.setty.android.network.SettyNetworkApi
import com.setty.android.network.apiCall

class OrderRepository(
    private val settyNetworkApi: SettyNetworkApi,
) {
    suspend fun getMyOrders(): List<Order> =
        apiCall { settyNetworkApi.getMyOrders() }.map { it.toOrder() }

    suspend fun getOrder(orderId: Long): Order =
        apiCall { settyNetworkApi.getOrder(orderId) }.toOrder()

    /** 취소 요청을 접수한다. 접수 시점의 주문 상태를 돌려주며 최종 결과는 주문 상세에서 확인한다. */
    suspend fun cancel(orderId: Long): OrderStatus? =
        orderStatusOrNull(apiCall { settyNetworkApi.cancelOrder(orderId) }.orderStatus)
}

internal fun OrderResponse.toOrder() = Order(
    id = id,
    listing = listing?.let {
        OrderedFurniture(
            id = it.id,
            name = it.name,
            thumbnailUrl = it.thumbnailUrl,
            price = it.price,
            deliveryFee = it.deliveryFee,
        )
    },
    orderStatus = orderStatusOrNull(orderStatus),
    deliveryStatus = deliveryStatusOf(deliveryStatus),
)
