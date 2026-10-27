package setty.platform.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.sql.DataSource;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import setty.platform.listing.storage.ListingImageStorage;
import setty.platform.member.repository.MemberRepository;
import setty.platform.order.controller.dto.OrderCreateRequest;
import setty.platform.order.service.OrderService;
import setty.platform.order.service.PendingOrderExpirationScheduler;
import setty.platform.order.service.PendingOrderExpirationService;
import setty.support.MySqlIntegrationTestSupport;

/** Bulk SQL must preserve the single-order path's state transitions, atomicity and locks in real MySQL. */
@SpringBootTest(properties = {
        "setty.events.resubmission-interval=PT24H",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "setty.platform.order.PendingOrderExpirationBatchTest$OrderLockObserver"
})
class PendingOrderExpirationBatchTest extends MySqlIntegrationTestSupport {

    private static final Instant REFERENCE = Instant.parse("2026-09-02T05:10:00Z");
    private static final Instant OLD_UPDATED_AT = REFERENCE.minusSeconds(600);
    private static final long SELLER = 101;
    private static final long BUYER = 202;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private DataSource dataSource;
    @Autowired private PendingOrderExpirationService expirationService;
    @Autowired private PendingOrderExpirationScheduler scheduler;
    @Autowired private OrderService orderService;
    @Autowired private MemberRepository members;
    @MockitoBean private ListingImageStorage listingImageStorage;
    @MockitoBean private Clock clock;

    @BeforeEach
    void setUp() {
        cleanUp();
        when(clock.instant()).thenReturn(REFERENCE);
        for (long id : List.of(SELLER, BUYER)) {
            jdbc.update("""
                    INSERT INTO members (id, login_id, password, role, phone_number, address, token)
                    VALUES (?, ?, 'encoded', 'MEMBER', '010-0000-0000', '테스트 주소', ?)
                    """, id, "expiration-" + id, "expiration-token-" + id);
        }
    }

    @AfterEach
    void cleanUp() {
        OrderLockObserver.attempted = null;
        if (jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE constraint_schema=DATABASE() AND table_name='listings'
                  AND constraint_name='test_expiration_release_failure'
                """, Integer.class) > 0) {
            jdbc.execute("ALTER TABLE listings DROP CHECK test_expiration_release_failure");
        }
        jdbc.update("DELETE FROM payments");
        jdbc.update("DELETE FROM delivery");
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM favorites");
        jdbc.update("DELETE FROM listing_images");
        jdbc.update("DELETE FROM listings");
        jdbc.update("DELETE FROM members");
    }

    @Test
    void 만료_경계와_모든_주문상태를_재확인하고_배송상태는_유지한다() {
        seed(1, "PENDING", REFERENCE, true);
        seed(2, "PENDING", REFERENCE.plusSeconds(1), true);
        seed(3, "PENDING", null, true);
        long id = 4;
        for (String status : List.of("CONFIRMED", "CANCEL_PENDING", "CANCELLED", "EXPIRED", "COMPLETED")) {
            seed(id++, status, REFERENCE.minusSeconds(1), true);
        }

        assertThat(expirationService.expireBatch(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 999L), REFERENCE))
                .isEqualTo(1);

        assertState(1, "EXPIRED", false);
        assertState(2, "PENDING", true);
        assertState(3, "PENDING", true);
        assertState(4, "CONFIRMED", true);
        assertState(5, "CANCEL_PENDING", true);
        assertState(6, "CANCELLED", true);
        assertState(7, "EXPIRED", true);
        assertState(8, "COMPLETED", true);
        assertThat(jdbc.queryForObject("SELECT delivery_status FROM orders WHERE id=1", String.class))
                .isEqualTo("PENDING");
        assertThat(expirationService.expireBatch(List.of(), REFERENCE)).isZero();
    }

    @Test
    void 삭제된_매물도_선점만_해제하고_이미_해제된_매물의_수정시각은_유지한다() {
        seed(1, "PENDING", REFERENCE, true);
        seed(2, "PENDING", REFERENCE, false);
        jdbc.update("UPDATE listings SET deleted_at=?, sale_status='RESERVED' WHERE id=1", sqlTime(REFERENCE));

        assertThat(expirationService.expireBatch(List.of(1L, 2L), REFERENCE)).isEqualTo(2);

        assertState(1, "EXPIRED", false);
        assertState(2, "EXPIRED", false);
        assertThat(jdbc.queryForObject("SELECT sale_status FROM listings WHERE id=1", String.class))
                .isEqualTo("RESERVED");
        assertThat(jdbc.queryForObject("SELECT UNIX_TIMESTAMP(deleted_at) FROM listings WHERE id=1", Long.class))
                .isEqualTo(REFERENCE.getEpochSecond());
        assertThat(jdbc.queryForObject("SELECT UNIX_TIMESTAMP(updated_at) FROM listings WHERE id=1", Long.class))
                .isGreaterThan(OLD_UPDATED_AT.getEpochSecond());
        assertThat(jdbc.queryForObject("SELECT UNIX_TIMESTAMP(updated_at) FROM listings WHERE id=2", Long.class))
                .isEqualTo(OLD_UPDATED_AT.getEpochSecond());
    }

    @Test
    void 만료_후_재주문하고_이전_후보를_재처리해도_새_선점은_유지된다() {
        seed(1, "PENDING", REFERENCE, true);
        assertThat(expirationService.expireBatch(List.of(1L), REFERENCE)).isEqualTo(1);

        final var nextOrder = orderService.pending(new OrderCreateRequest(1L), members.findById(BUYER).orElseThrow());
        assertThat(expirationService.expireBatch(List.of(1L), REFERENCE.plusSeconds(1))).isZero();

        assertThat(nextOrder.getId()).isNotEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE listing_id=1", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT order_status FROM orders WHERE id=?", String.class, nextOrder.getId()))
                .isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT has_purchase_request FROM listings WHERE id=1", Boolean.class)).isTrue();
    }

    @Test
    void 매물_갱신_실패시_묶음_전체가_롤백되고_스케줄러는_정상_주문을_단건으로_복구한다() {
        for (long id = 1; id <= 3; id++) {
            seed(id, "PENDING", REFERENCE, true);
        }
        // 이 테스트의 임시 DB에서만 매물 2의 변경을 실패시킨다.
        jdbc.update("UPDATE listings SET title='test-expiration-failure' WHERE id=2");
        jdbc.execute("""
                ALTER TABLE listings ADD CONSTRAINT test_expiration_release_failure
                CHECK (title <> 'test-expiration-failure' OR has_purchase_request = true)
                """);

        assertThatThrownBy(() -> expirationService.expireBatch(List.of(1L, 2L, 3L), REFERENCE))
                .isInstanceOf(RuntimeException.class);
        for (long id = 1; id <= 3; id++) {
            assertState(id, "PENDING", true);
        }

        scheduler.expirePendingOrders();

        assertState(1, "EXPIRED", false);
        assertState(2, "PENDING", true);
        assertState(3, "EXPIRED", false);
    }

    @Test
    void 실제_주문_잠금을_기다리는_동안_결제가_확정되면_그_주문은_제외한다() throws Exception {
        seed(1, "PENDING", REFERENCE, true);
        seed(2, "PENDING", REFERENCE, true);
        try (Connection payment = dataSource.getConnection();
             var executor = Executors.newSingleThreadExecutor()) {
            payment.setAutoCommit(false);
            try {
                try (var statement = payment.createStatement()) {
                    statement.executeQuery("SELECT id FROM orders WHERE id=1 FOR UPDATE").close();
                }
                final var lockAttempted = new CountDownLatch(1);
                OrderLockObserver.attempted = lockAttempted;
                final var expiration = executor.submit(() -> expirationService.expireBatch(List.of(1L, 2L), REFERENCE));
                // 잠금 SQL 제출과 미완료를 확인하고 결제를 확정한다. 실제 MySQL 잠금을 사용한다.
                assertThat(lockAttempted.await(5, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> expiration.get(100, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);
                try (var statement = payment.createStatement()) {
                    statement.executeUpdate("UPDATE orders SET order_status='CONFIRMED', delivery_status='REQUESTED' WHERE id=1");
                }
                payment.commit();
                assertThat(expiration.get(5, TimeUnit.SECONDS)).isEqualTo(1);
            } finally {
                payment.rollback();
            }
        }
        assertState(1, "CONFIRMED", true);
        assertState(2, "EXPIRED", false);
    }

    @Test
    void 같은_후보를_동시에_처리해도_중복_만료하지_않는다() throws Exception {
        seed(1, "PENDING", REFERENCE, true);
        seed(2, "PENDING", REFERENCE, true);
        final var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            final var first = executor.submit(() -> {
                start.await();
                return expirationService.expireBatch(List.of(1L, 2L), REFERENCE);
            });
            final var second = executor.submit(() -> {
                start.await();
                return expirationService.expireBatch(List.of(2L, 1L), REFERENCE);
            });
            start.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS) + second.get(5, TimeUnit.SECONDS)).isEqualTo(2);
        }
        assertState(1, "EXPIRED", false);
        assertState(2, "EXPIRED", false);
    }

    @ParameterizedTest
    @CsvSource({"1,1", "500,200", "500,500"})
    void 만료_커밋_전_동일_매물은_거절하지만_다른_매물의_주문은_막지_않는다(int due, int batchSize) throws Exception {
        for (int id = 1; id <= due; id++) seed(id, "PENDING", REFERENCE, true);
        seed(1001, "EXPIRED", REFERENCE, false);
        final var buyer = members.findById(BUYER).orElseThrow();
        final var ids = java.util.stream.LongStream.rangeClosed(1, batchSize).boxed().toList();
        final var updated = new CountDownLatch(1);
        final var allowCommit = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            final var expiry = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                int count = expirationService.expireBatch(ids, REFERENCE);
                updated.countDown();
                try {
                    if (!allowCommit.await(10, TimeUnit.SECONDS)) throw new AssertionError("커밋 허용 대기 초과");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                return count;
            }));
            try {
                assertThat(updated.await(5, TimeUnit.SECONDS)).isTrue();
                // 타임아웃은 성능 목표가 아니라, 잠금 대기를 회귀 실패로 잡기 위한 테스트 제한이다.
                executor.submit(() -> assertThatThrownBy(() -> orderService.pending(new OrderCreateRequest(1L), buyer))
                        .isInstanceOfSatisfying(BusinessException.class,
                                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ALREADY_ORDERED)))
                        .get(5, TimeUnit.SECONDS);
                final var unrelated = executor.submit(() -> orderService.pending(new OrderCreateRequest(1001L), buyer));
                assertThat(unrelated.get(5, TimeUnit.SECONDS).getListingId()).isEqualTo(1001L);
                assertThat(expiry.isDone()).isFalse();
            } finally {
                allowCommit.countDown();
            }
            assertThat(expiry.get(5, TimeUnit.SECONDS)).isEqualTo(batchSize);
        }
        assertThat(orderService.pending(new OrderCreateRequest(1L), buyer).getListingId()).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE listing_id=1 AND order_status='PENDING'", Integer.class))
                .isEqualTo(1);
    }

    private void seed(long id, String status, Instant expiresAt, boolean reserved) {
        jdbc.update("""
                INSERT INTO listings (id,seller_id,title, display_title,description,price,delivery_fee,category,condition_grade,
                    width_cm,depth_cm,height_cm,sale_status,has_purchase_request,created_at,updated_at)
                VALUES (?,?,'테스트 책상', '테스트 책상','테스트 설명',10000,10000,'DESK','A',60,60,70,'AVAILABLE',?,?,?)
                """, id, SELLER, reserved, sqlTime(OLD_UPDATED_AT), sqlTime(OLD_UPDATED_AT));
        jdbc.update("""
                INSERT INTO orders (id,listing_id,buyer_id,delivery_status,order_status,pending_expires_at)
                VALUES (?,?,?,'PENDING',?,?)
                """, id, id, BUYER, status, expiresAt == null ? null : sqlTime(expiresAt));
    }

    private static String sqlTime(Instant time) {
        return time.toString().replace('T', ' ').replace("Z", "");
    }

    private void assertState(long id, String status, boolean reserved) {
        assertThat(jdbc.queryForObject("SELECT order_status FROM orders WHERE id=?", String.class, id)).isEqualTo(status);
        assertThat(jdbc.queryForObject("SELECT has_purchase_request FROM listings WHERE id=?", Boolean.class, id))
                .isEqualTo(reserved);
    }

    public static class OrderLockObserver implements StatementInspector {
        static volatile CountDownLatch attempted;

        @Override
        public String inspect(String sql) {
            final var latch = attempted;
            if (latch != null && sql.contains("from orders") && sql.contains("for update")) {
                latch.countDown();
            }
            return sql;
        }
    }
}
