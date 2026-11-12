package setty.platform.order.service;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import setty.platform.order.domain.OrderStatus;
import setty.platform.order.repository.OrderRepository;

/**
 * 확정되지 못하고 PAID에 머문 주문을 다시 확정한다. 이벤트 재발행이 끝난 뒤에도 남은 주문이 대상이다.
 *
 * <p>PAID가 된 시각을 따로 저장하지 않는다. PAID는 결제 만료 시각 전에만 되므로,
 * 만료 시각에서 기준 시간이 지난 PAID 주문은 PAID가 된 지 적어도 그만큼 지났다.
 * 다시 확정해도 실패하면 에러 로그를 남기고, 남은 건수를 지표로 내 운영자가 확인한다(docs/policy/order.md 확인 필요).
 */
@Component
public class PaidOrderConfirmationScheduler {

    private static final Logger log = LoggerFactory.getLogger(PaidOrderConfirmationScheduler.class);

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final Clock clock;
    private final Duration stuckAfter;
    private final AtomicLong stuckCount = new AtomicLong();
    private final AtomicLong lastRunEpochSecond = new AtomicLong();

    public PaidOrderConfirmationScheduler(
            final OrderRepository orderRepository,
            final OrderService orderService,
            final Clock clock,
            @Value("${setty.order.paid-confirmation.stuck-after:PT15M}") final Duration stuckAfter,
            final ObjectProvider<MeterRegistry> meterRegistries
    ) {
        this.orderRepository = orderRepository;
        this.orderService = orderService;
        this.clock = clock;
        this.stuckAfter = stuckAfter;
        final MeterRegistry meterRegistry = meterRegistries.getIfAvailable();
        if (meterRegistry != null) {
            Gauge.builder("setty.order.paid.stuck", stuckCount, AtomicLong::get)
                    .description("다시 확정해도 PAID에 남은 주문 수")
                    .register(meterRegistry);
            Gauge.builder("setty.order.paid.confirmation.last-run", lastRunEpochSecond, AtomicLong::get)
                    .description("PAID 재확정 마지막 실행 시각(epoch 초)")
                    .register(meterRegistry);
        }
    }

    @Scheduled(
            fixedDelayString = "${setty.order.paid-confirmation.scan-interval:PT5M}",
            initialDelayString = "${setty.order.paid-confirmation.scan-interval:PT5M}"
    )
    public void confirmStuckPaidOrders() {
        final Instant now = clock.instant();
        final List<Long> orderIds = orderRepository.findExpiredOrderIds(OrderStatus.PAID, now.minus(stuckAfter));
        long stillPaid = 0;
        for (final Long orderId : orderIds) {
            try {
                orderService.confirmPaid(orderId);
                log.warn("PAID에 머문 주문을 다시 확정했습니다. orderId={}", orderId);
            } catch (final RuntimeException exception) {
                stillPaid++;
                log.error("PAID에 머문 주문을 다시 확정하지 못했습니다. 수동 확인이 필요합니다. orderId={}", orderId, exception);
            }
        }
        stuckCount.set(stillPaid);
        lastRunEpochSecond.set(now.getEpochSecond());
    }
}
