package setty.platform.order.service;

import java.time.Clock;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import setty.common.OrderCompleted;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;
import setty.platform.order.domain.Order;
import setty.platform.order.repository.OrderRepository;

@Service
public class OrderCompletionService {

    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public OrderCompletionService(
            final OrderRepository orderRepository,
            final ApplicationEventPublisher eventPublisher,
            final Clock clock
    ) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    // 구매자가 배송 완료를 확인한다. 이미 판매 완료된 주문에 다시 확인해도 성공으로 응답한다.
    @Transactional
    public void confirmByBuyer(final Long orderId, final Long buyerId) {
        if (orderId == null || orderId <= 0 || buyerId == null || buyerId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        final Order order = orderRepository.findByIdAndBuyerIdForUpdate(orderId, buyerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        complete(order, clock.instant());
    }

    // 스캔 이후 구매자 확인이나 취소로 상태가 바뀌었을 수 있어 잠근 뒤 다시 판단한다.
    @Transactional
    public boolean completeIfDue(final Long orderId, final Instant referenceTime) {
        final Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (order == null || !order.canAutoComplete(referenceTime)) {
            return false;
        }
        return complete(order, referenceTime);
    }

    private boolean complete(final Order order, final Instant completedAt) {
        if (!order.complete()) {
            return false;
        }
        eventPublisher.publishEvent(new OrderCompleted(order.getId(), completedAt));
        return true;
    }
}
