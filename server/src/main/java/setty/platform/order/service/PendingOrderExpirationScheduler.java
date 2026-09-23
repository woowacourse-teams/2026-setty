package setty.platform.order.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import setty.common.DeliveryStatus;
import setty.platform.order.repository.OrderRepository;

@Component
public class PendingOrderExpirationScheduler {

    private static final Logger log = LoggerFactory.getLogger(PendingOrderExpirationScheduler.class);

    private final OrderRepository orderRepository;
    private final PendingOrderExpirationService expirationService;
    private final Clock clock;

    public PendingOrderExpirationScheduler(
            final OrderRepository orderRepository,
            final PendingOrderExpirationService expirationService,
            final Clock clock
    ) {
        this.orderRepository = orderRepository;
        this.expirationService = expirationService;
        this.clock = clock;
    }

    @Scheduled(
            fixedDelayString = "${setty.order.pending-expiration.scan-interval:PT1M}",
            initialDelayString = "${setty.order.pending-expiration.scan-interval:PT1M}"
    )
    public void expirePendingOrders() {
        final Instant referenceTime = clock.instant();
        final List<Long> expiredOrderIds = orderRepository.findExpiredOrderIds(
                DeliveryStatus.PENDING,
                referenceTime
        );

        for (final Long orderId : expiredOrderIds) {
            try {
                expirationService.expire(orderId, referenceTime);
            } catch (final RuntimeException exception) {
                log.error("PENDING 주문 자동 만료에 실패했습니다. orderId={}", orderId, exception);
            }
        }
    }
}
