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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import setty.delivery.domain.DeliveryId;
import setty.delivery.domain.DriverId;
import setty.delivery.domain.OrderId;
import setty.delivery.domain.delivery.DeliveryPoint;
import setty.delivery.domain.delivery.DeliveryRoute;
import setty.delivery.domain.delivery.EstimatedDeliveryFee;
import setty.delivery.domain.delivery.FurnitureInfo;
import setty.global.exception.BusinessException;

@SpringBootTest
@Testcontainers
class DeliveryConcurrencyIntegrationTest {

    private static final long ORDER_ID = 101L;
    private static final Instant NOW = Instant.parse("2026-08-26T01:00:00Z");

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4.11")
            .withDatabaseName("setty_test")
            .withUsername("setty_test")
            .withPassword("setty_test");

    @Autowired
    private RegisterDeliveryService registerDeliveryService;

    @Autowired
    private DeliveryLifecycleService deliveryLifecycleService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM delivery_order_lock");
        jdbcTemplate.update("DELETE FROM delivery_cancellation");
        jdbcTemplate.update("DELETE FROM delivery");
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
        if (accepted) {
            assertThat(deliveryStatus()).isEqualTo("ACCEPTED");
            assertThat(cancellationCount()).isZero();
        } else {
            assertThat(deliveryStatus()).isEqualTo("CANCELLED");
            assertThat(cancellationCount()).isOne();
        }
    }

    @Test
    void cancelledOrderNeverKeepsRequestedDelivery() throws Exception {
        runConcurrently(
                succeeds(this::register),
                succeeds(() -> deliveryLifecycleService.cancel(OrderId.from(ORDER_ID), "cancel-request-1", NOW))
        );

        assertThat(cancellationCount()).isOne();
        final List<String> statuses = jdbcTemplate.queryForList(
                "SELECT status FROM delivery WHERE order_id = ?", String.class, ORDER_ID
        );
        assertThat(statuses).isIn(List.of(), List.of("CANCELLED"));
    }

    private DeliveryId register() {
        registerDeliveryService.register(
                OrderId.from(ORDER_ID),
                FurnitureInfo.of("가상 원목 의자", "CHAIR"),
                DeliveryRoute.of(
                        DeliveryPoint.pickup("서울시 가상구 출발로 1", "010-0000-0001"),
                        DeliveryPoint.destination("서울시 가상구 도착로 2", "010-0000-0002")
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

    private long cancellationCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM delivery_cancellation", Long.class);
    }
}
