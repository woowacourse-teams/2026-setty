package setty.settlement.application;

import static org.assertj.core.api.Assertions.assertThat;
import static setty.global.event.EventPublicationTestSupport.awaitEventsHandled;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import setty.common.DeliveryDelivered;
import setty.common.OrderCompleted;
import setty.common.OrderConfirmed;
import setty.settlement.domain.Settlement;
import setty.settlement.persistence.SettlementRepository;
import setty.support.MySqlIntegrationTestSupport;

@SpringBootTest
class SettlementServiceIntegrationTest extends MySqlIntegrationTestSupport {

    private static final long ORDER_ID = 1L;
    private static final long SELLER_ID = 10L;
    private static final long DRIVER_ID = 20L;
    private static final long LISTING_ID = 100L;
    private static final int ITEM_PRICE = 150_000;
    private static final int DELIVERY_FEE = 10_000;
    private static final Instant RECORDED_AT = Instant.parse("2026-10-06T01:00:00Z");
    private static final Instant DECIDED_AT = Instant.parse("2026-10-09T01:00:00Z");

    @Autowired
    private SettlementService settlementService;

    @Autowired
    private SettlementRepository settlementRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void cleanUp() {
        awaitEventsHandled(jdbcTemplate);
        jdbcTemplate.update("DELETE FROM EVENT_PUBLICATION");
        jdbcTemplate.update("DELETE FROM delivery");
        jdbcTemplate.update("DELETE FROM delivery_order_decision");
        jdbcTemplate.update("DELETE FROM settlements");
        jdbcTemplate.update("DELETE FROM settlement_order");
    }

    @Test
    void 판매_완료되면_판매자와_기사_정산이_확정된다() {
        recordSeller(ORDER_ID);
        recordDriver(ORDER_ID);

        settlementService.complete(ORDER_ID, DECIDED_AT);

        assertThat(statusOf(ORDER_ID, "SELLER")).isEqualTo("CONFIRMED");
        assertThat(statusOf(ORDER_ID, "DRIVER")).isEqualTo("CONFIRMED");
    }

    @Test
    void 판매_완료가_먼저_도착해도_나중에_기록되는_정산은_확정된다() {
        settlementService.complete(ORDER_ID, DECIDED_AT);

        recordDriver(ORDER_ID);
        recordSeller(ORDER_ID);

        assertThat(statusOf(ORDER_ID, "SELLER")).isEqualTo("CONFIRMED");
        assertThat(statusOf(ORDER_ID, "DRIVER")).isEqualTo("CONFIRMED");
    }

    @Test
    void 취소가_먼저_도착해도_나중에_기록되는_판매자_정산은_취소된다() {
        settlementService.cancel(ORDER_ID, DECIDED_AT);

        recordSeller(ORDER_ID);

        assertThat(statusOf(ORDER_ID, "SELLER")).isEqualTo("CANCELLED");
    }

    @Test
    void 판매_완료_전에는_정산이_대기_상태다() {
        recordSeller(ORDER_ID);
        recordDriver(ORDER_ID);

        assertThat(statusOf(ORDER_ID, "SELLER")).isEqualTo("PENDING");
        assertThat(statusOf(ORDER_ID, "DRIVER")).isEqualTo("PENDING");
    }

    @Test
    void 같은_이벤트가_다시_와도_정산은_한_번만_기록된다() {
        recordSeller(ORDER_ID);
        recordSeller(ORDER_ID);
        recordDriver(ORDER_ID);
        recordDriver(ORDER_ID);
        settlementService.complete(ORDER_ID, DECIDED_AT);
        settlementService.complete(ORDER_ID, DECIDED_AT.plusSeconds(60));

        assertThat(settlementCount(ORDER_ID)).isEqualTo(2);
        assertThat(settlementRepository.findAllByOrderId(ORDER_ID))
                .extracting(Settlement::getConfirmedAt)
                .containsOnly(DECIDED_AT);
    }

    @Test
    void 취소된_주문에_판매_완료가_와도_취소가_유지된다() {
        recordSeller(ORDER_ID);
        settlementService.cancel(ORDER_ID, DECIDED_AT);

        settlementService.complete(ORDER_ID, DECIDED_AT.plusSeconds(60));

        assertThat(statusOf(ORDER_ID, "SELLER")).isEqualTo("CANCELLED");
    }

    @Test
    void 기사_정산_기록과_판매_완료가_동시에_처리돼도_정산이_확정된다() throws Exception {
        final List<Long> orderIds = LongStream.rangeClosed(1, 20).boxed().toList();
        final ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (final Long orderId : orderIds) {
                final CountDownLatch start = new CountDownLatch(1);
                final CompletableFuture<Void> record = CompletableFuture.runAsync(() -> {
                    await(start);
                    recordDriver(orderId);
                }, executor);
                final CompletableFuture<Void> complete = CompletableFuture.runAsync(() -> {
                    await(start);
                    settlementService.complete(orderId, DECIDED_AT);
                }, executor);
                start.countDown();
                CompletableFuture.allOf(record, complete).get();
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(orderIds).allSatisfy(orderId ->
                assertThat(statusOf(orderId, "DRIVER")).isEqualTo("CONFIRMED"));
    }

    @Test
    void 주문_확정부터_판매_완료까지_이벤트로_정산이_확정된다() {
        publishCommitted(new OrderConfirmed(
                ORDER_ID,
                "가상 원목 의자",
                "CHAIR",
                "서울시 가상구 출발로 1",
                "서울시 가상구 도착로 2",
                DELIVERY_FEE,
                "010-0000-0001",
                "010-0000-0002",
                LISTING_ID,
                SELLER_ID,
                ITEM_PRICE
        ));
        publishCommitted(new DeliveryDelivered(5L, ORDER_ID, RECORDED_AT, DRIVER_ID, DELIVERY_FEE));
        publishCommitted(new OrderCompleted(ORDER_ID, DECIDED_AT));

        assertThat(statusOf(ORDER_ID, "SELLER")).isEqualTo("CONFIRMED");
        assertThat(statusOf(ORDER_ID, "DRIVER")).isEqualTo("CONFIRMED");
        // 수수료가 없으므로 판매자·기사 정산의 합은 구매자가 결제한 물품 가격과 배송비의 합과 같다.
        assertThat(jdbcTemplate.queryForObject(
                "SELECT SUM(amount) FROM settlements WHERE order_id = ? AND status = 'CONFIRMED'",
                Integer.class,
                ORDER_ID
        )).isEqualTo(ITEM_PRICE + DELIVERY_FEE);
    }

    private void recordSeller(final long orderId) {
        settlementService.recordSeller(orderId, SELLER_ID, LISTING_ID, ITEM_PRICE, RECORDED_AT);
    }

    private void recordDriver(final long orderId) {
        settlementService.recordDriver(orderId, DRIVER_ID, DELIVERY_FEE, RECORDED_AT);
    }

    private void publishCommitted(final Object event) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> eventPublisher.publishEvent(event));
        awaitEventsHandled(jdbcTemplate);
    }

    private String statusOf(final long orderId, final String payeeType) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM settlements WHERE order_id = ? AND payee_type = ?",
                String.class,
                orderId,
                payeeType
        );
    }

    private long settlementCount(final long orderId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM settlements WHERE order_id = ?",
                Long.class,
                orderId
        );
    }

    private static void await(final CountDownLatch latch) {
        try {
            latch.await();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
