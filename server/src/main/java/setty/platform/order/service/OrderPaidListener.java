package setty.platform.order.service;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;

/**
 * PAID가 커밋된 뒤 새 트랜잭션에서 주문을 확정한다. 확정이 실패해도 결제와 PAID는 이미 커밋돼 있다.
 * 실패하면 발행 기록이 남아 재발행되고, 그래도 남은 PAID는 {@link PaidOrderConfirmationScheduler}가 다시 확정한다.
 */
@Component
public class OrderPaidListener {

    private final OrderService orderService;

    public OrderPaidListener(final OrderService orderService) {
        this.orderService = orderService;
    }

    @ApplicationModuleListener
    public void onOrderPaid(final OrderPaid event) {
        if (event == null || event.orderId() == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        orderService.confirmPaid(event.orderId());
    }
}
