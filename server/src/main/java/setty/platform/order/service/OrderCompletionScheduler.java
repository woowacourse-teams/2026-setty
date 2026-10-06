package setty.platform.order.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import setty.platform.order.domain.Order;
import setty.platform.order.repository.OrderRepository;

@Component
public class OrderCompletionScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderCompletionScheduler.class);

    private final OrderRepository orderRepository;
    private final OrderCompletionService completionService;
    private final Clock clock;

    public OrderCompletionScheduler(
            final OrderRepository orderRepository,
            final OrderCompletionService completionService,
            final Clock clock
    ) {
        this.orderRepository = orderRepository;
        this.completionService = completionService;
        this.clock = clock;
    }

    @Scheduled(
            fixedDelayString = "${setty.order.completion.scan-interval:PT1M}",
            initialDelayString = "${setty.order.completion.scan-interval:PT1M}"
    )
    public void completeDueOrders() {
        final Instant referenceTime = clock.instant();
        final List<Long> dueOrderIds = orderRepository.findCompletionDueOrderIds(
                referenceTime.minus(Order.COMPLETION_WAITING_PERIOD)
        );

        for (final Long orderId : dueOrderIds) {
            try {
                completionService.completeIfDue(orderId, referenceTime);
            } catch (final RuntimeException exception) {
                log.error("배송 완료 주문 자동 판매 완료에 실패했습니다. orderId={}", orderId, exception);
            }
        }
    }
}
