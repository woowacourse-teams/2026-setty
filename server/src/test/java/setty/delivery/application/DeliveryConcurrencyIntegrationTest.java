package setty.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import setty.delivery.domain.DeliveryId;
import setty.delivery.domain.DriverId;
import setty.delivery.domain.OrderId;
import setty.delivery.domain.delivery.DeliveryPoint;
import setty.delivery.domain.delivery.DeliveryRoute;
import setty.delivery.domain.delivery.EstimatedDeliveryFee;
import setty.delivery.domain.delivery.FurnitureInfo;
import setty.global.exception.BusinessException;
import setty.support.MySqlIntegrationTestSupport;

@SpringBootTest
class DeliveryConcurrencyIntegrationTest extends MySqlIntegrationTestSupport {

    private static final long ORDER_ID = 101L;
    private static final Instant NOW = Instant.parse("2026-08-26T01:00:00Z");
    private static final DriverId DRIVER_ID = new DriverId(201L);

    @Autowired
    private RegisterDeliveryService registerDeliveryService;

    @Autowired
    private DeliveryLifecycleService deliveryLifecycleService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM delivery");
        jdbcTemplate.update("DELETE FROM delivery_order_decision");
    }

    @Test
    void onlyOneOfConcurrentAcceptancesSucceeds() throws Exception {
        final DeliveryId deliveryId = register();

        final List<Boolean> results = runConcurrently(
                succeeds(() -> deliveryLifecycleService.accept(deliveryId, new DriverId(201L), NOW)),
                succeeds(() -> deliveryLifecycleService.accept(deliveryId, new DriverId(202L), NOW))
        );

        assertThat(results).containsExactlyInAnyOrder(true, false);
        assertThat(deliveryStatus()).isEqualTo("ACCEPTED");
    }

    @Test
    void acceptanceAndCancellationDoNotBothTakeEffect() throws Exception {
        final DeliveryId deliveryId = register();

        final List<Boolean> results = runConcurrently(
                succeeds(() -> deliveryLifecycleService.accept(deliveryId, new DriverId(201L), NOW)),
                succeeds(() -> deliveryLifecycleService.cancel(OrderId.from(ORDER_ID), "cancel-request-1", NOW))
        );

        final boolean accepted = results.getFirst();
        assertThat(results.getLast()).isTrue();
        assertThat(deliveryStatus()).isEqualTo(accepted ? "ACCEPTED" : "CANCELLED");
    }

    @RepeatedTest(20)
    void assignmentCancellationAndPickUpDoNotBothTakeEffect() throws Exception {
        final DeliveryId deliveryId = register();
        deliveryLifecycleService.accept(deliveryId, DRIVER_ID, NOW);

        final List<Boolean> results = runConcurrently(
                succeeds(() -> deliveryLifecycleService.cancelAssignment(deliveryId, DRIVER_ID, NOW)),
                succeeds(() -> deliveryLifecycleService.pickUp(deliveryId, DRIVER_ID, NOW))
        );

        assertThat(results).containsExactlyInAnyOrder(true, false);
        final boolean cancelled = results.getFirst();
        assertThat(deliveryStatus()).isEqualTo(cancelled ? "REQUESTED" : "PICKED_UP");
        assertThat(driverId()).isEqualTo(cancelled ? null : DRIVER_ID.value());
    }

    @RepeatedTest(20)
    void assignmentCancellationAndBuyerCancellationFollowWhicheverCommitsFirst() throws Exception {
        final DeliveryId deliveryId = register();
        deliveryLifecycleService.accept(deliveryId, DRIVER_ID, NOW);

        final List<Boolean> results = runConcurrently(
                succeeds(() -> deliveryLifecycleService.cancelAssignment(deliveryId, DRIVER_ID, NOW)),
                succeeds(() -> deliveryLifecycleService.cancel(OrderId.from(ORDER_ID), "cancel-request-1", NOW))
        );

        // 구매자 취소는 거절돼도 예외 없이 거절 결과를 발행하므로, 결과는 배송 상태로 가린다.
        assertThat(results).containsExactly(true, true);
        assertThat(deliveryStatus()).isIn("CANCELLED", "REQUESTED");
        assertThat(driverId()).isNull();
    }

    private Long driverId() {
        return jdbcTemplate.queryForObject(
                "SELECT driver_id FROM delivery WHERE order_id = ?", Long.class, ORDER_ID
        );
    }

    private DeliveryId register() {
        registerDeliveryService.register(
                OrderId.from(ORDER_ID),
                FurnitureInfo.of("가상 원목 의자", "CHAIR"),
                DeliveryRoute.of(
                        DeliveryPoint.pickup("서울시 가상구 출발로 1", "010-0000-0001", null, null),
                        DeliveryPoint.destination("서울시 가상구 도착로 2", "010-0000-0002", null, null)
                ),
                EstimatedDeliveryFee.from(10_000),
                NOW
        );
        final List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM delivery WHERE order_id = ?", Long.class, ORDER_ID
        );
        return ids.isEmpty() ? null : new DeliveryId(ids.getFirst());
    }

    @SafeVarargs
    private static List<Boolean> runConcurrently(final Callable<Boolean>... tasks) throws Exception {
        final ExecutorService executor = Executors.newFixedThreadPool(tasks.length);
        final CountDownLatch start = new CountDownLatch(1);
        try {
            final List<Future<Boolean>> futures = Arrays.stream(tasks)
                    .map(task -> executor.submit(() -> {
                        start.await();
                        return task.call();
                    }))
                    .toList();
            start.countDown();
            final List<Boolean> results = new ArrayList<>();
            for (final Future<Boolean> future : futures) {
                results.add(future.get(10, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private static Callable<Boolean> succeeds(final Runnable action) {
        return () -> {
            try {
                action.run();
                return true;
            } catch (final BusinessException exception) {
                return false;
            }
        };
    }

    private String deliveryStatus() {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM delivery WHERE order_id = ?", String.class, ORDER_ID
        );
    }

}
