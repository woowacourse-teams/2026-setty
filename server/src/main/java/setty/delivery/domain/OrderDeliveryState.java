package setty.delivery.domain;

import setty.common.DeliveryStatus;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;

public class OrderDeliveryState {

    private final OrderId orderId;
    private DeliveryStatus status;

    public OrderDeliveryState(final OrderId orderId, final DeliveryStatus status) {
        if (orderId == null || status == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        this.orderId = orderId;
        this.status = status;
    }

    public boolean synchronizeTo(final DeliveryStatus newStatus) {
        final DeliveryStatus expectedPreviousStatus = expectedPreviousStatusOf(newStatus);
        if (status == newStatus) {
            return false;
        }
        if (status != expectedPreviousStatus) {
            throw new BusinessException(ErrorCode.ORDER_DELIVERY_STATUS_MISMATCH);
        }
        status = newStatus;
        return true;
    }

    private DeliveryStatus expectedPreviousStatusOf(final DeliveryStatus newStatus) {
        if (newStatus == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        return switch (newStatus) {
            case ACCEPTED -> DeliveryStatus.REQUESTED;
            case PICKED_UP -> DeliveryStatus.ACCEPTED;
            case DELIVERED -> DeliveryStatus.PICKED_UP;
            case REQUESTED -> throw new BusinessException(ErrorCode.INVALID_REQUEST);
        };
    }

    public OrderId getOrderId() {
        return orderId;
    }

    public DeliveryStatus getStatus() {
        return status;
    }
}
