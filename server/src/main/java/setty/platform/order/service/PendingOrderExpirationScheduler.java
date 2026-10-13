package setty.platform.order.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import setty.platform.order.domain.OrderStatus;
import setty.platform.order.repository.OrderRepository;

@Component
public class PendingOrderExpirationScheduler {

    private static final Logger log = LoggerFactory.getLogger(PendingOrderExpirationScheduler.class);
    // 한 번에 잠그는 주문 수를 제한하면서 반복 SQL과 커밋을 줄인다.
    private static final int BATCH_SIZE = 200;

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
                OrderStatus.PENDING,
                referenceTime
        );

        for (int from = 0; from < expiredOrderIds.size(); from += BATCH_SIZE) {
            final List<Long> batch = expiredOrderIds.subList(from, Math.min(from + BATCH_SIZE, expiredOrderIds.size()));
            try {
                expirationService.expireBatch(batch, referenceTime);
            } catch (final RuntimeException exception) {
                // 서비스 호출이 롤백된 뒤 별도 트랜잭션으로 재시도하여 한 건의 오류를 격리한다.
                log.warn("PENDING 주문 묶음 만료 실패, 단건으로 재시도합니다. orderIds={}", batch, exception);
                expireIndividually(batch, referenceTime);
            }
        }
    }

    private void expireIndividually(final List<Long> orderIds, final Instant referenceTime) {
        for (final Long orderId : orderIds) {
            try {
                expirationService.expire(orderId, referenceTime);
            } catch (final RuntimeException exception) {
                log.error("PENDING 주문 자동 만료에 실패했습니다. orderId={}", orderId, exception);
            }
        }
    }
}
