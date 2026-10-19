package cloud.setty.setty.data

import cloud.setty.setty.model.Order
import cloud.setty.setty.model.OrderStatus
import cloud.setty.setty.model.OrderedFurniture
import cloud.setty.setty.model.deliveryStatusOf
import cloud.setty.setty.model.orderStatusOrNull
import cloud.setty.setty.network.OrderResponse
import cloud.setty.setty.network.SettyNetworkApi
import cloud.setty.setty.network.apiCall

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
