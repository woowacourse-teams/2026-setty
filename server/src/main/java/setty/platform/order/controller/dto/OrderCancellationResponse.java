package setty.platform.order.controller.dto;

import setty.platform.order.domain.Order;

public record OrderCancellationResponse(
        Long orderId,
        String orderStatus,
        String message
) {

    public static OrderCancellationResponse from(final Order order) {
        return new OrderCancellationResponse(
                order.getId(),
                order.getOrderStatus().name(),
                "취소 대기 중"
        );
    }
}
