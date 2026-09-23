package setty.platform.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import setty.common.DeliveryStatusChanged;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;
import setty.platform.listing.storage.ListingImageStorage;
import setty.platform.order.service.SyncOrderDeliveryStatusService;

@SpringBootTest
@Testcontainers
class SyncOrderDeliveryStatusServiceTest {

    private static final long SELLER_ID = 101L;
    private static final long BUYER_ID = 202L;
    private static final long LISTING_ID = 11L;
    private static final long ORDER_ID = 1L;

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4.11")
            .withDatabaseName("setty_test")
            .withUsername("setty_test")
            .withPassword("setty_test");

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private ListingImageStorage listingImageStorage;

    @Autowired
    private SyncOrderDeliveryStatusService syncOrderDeliveryStatusService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        cleanUp();
        insertMember(SELLER_ID);
        insertMember(BUYER_ID);
        insertListing(LISTING_ID, SELLER_ID);
        insertOrder(ORDER_ID, LISTING_ID, BUYER_ID, "REQUESTED");
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM delivery");
        jdbcTemplate.update("DELETE FROM orders");
        jdbcTemplate.update("DELETE FROM listing_images");
        jdbcTemplate.update("DELETE FROM listings");
        jdbcTemplate.update("DELETE FROM members");
    }

    @Test
    void 배송_상태_변경_이벤트를_수신하면_주문_상태가_갱신된다() {
        eventPublisher.publishEvent(
                new DeliveryStatusChanged(1L, ORDER_ID, "ACCEPTED", Instant.now()));

        assertThat(deliveryStatusOf(ORDER_ID)).isEqualTo("ACCEPTED");
    }

    @Test
    void 상태는_순서대로_끝까지_전이된다() {
        eventPublisher.publishEvent(new DeliveryStatusChanged(1L, ORDER_ID, "ACCEPTED", Instant.now()));
        eventPublisher.publishEvent(new DeliveryStatusChanged(1L, ORDER_ID, "PICKED_UP", Instant.now()));
        eventPublisher.publishEvent(new DeliveryStatusChanged(1L, ORDER_ID, "DELIVERED", Instant.now()));

        assertThat(deliveryStatusOf(ORDER_ID)).isEqualTo("DELIVERED");
    }

    @Test
    void 역행_이벤트는_거부되고_상태가_유지된다() {
        eventPublisher.publishEvent(new DeliveryStatusChanged(1L, ORDER_ID, "ACCEPTED", Instant.now()));
        eventPublisher.publishEvent(new DeliveryStatusChanged(1L, ORDER_ID, "PICKED_UP", Instant.now()));

        assertThatThrownBy(() -> eventPublisher.publishEvent(
                new DeliveryStatusChanged(1L, ORDER_ID, "ACCEPTED", Instant.now())))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ORDER_DELIVERY_STATUS_MISMATCH);
        assertThat(deliveryStatusOf(ORDER_ID)).isEqualTo("PICKED_UP");
    }

    @Test
    void 같은_상태_중복_이벤트는_무시된다() {
        eventPublisher.publishEvent(new DeliveryStatusChanged(1L, ORDER_ID, "ACCEPTED", Instant.now()));

        eventPublisher.publishEvent(new DeliveryStatusChanged(1L, ORDER_ID, "ACCEPTED", Instant.now()));

        assertThat(deliveryStatusOf(ORDER_ID)).isEqualTo("ACCEPTED");
    }

    @Test
    void 주문_행_잠금은_외부_트랜잭션이_끝날_때까지_유지된다() throws Exception {
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            final CompletableFuture<Void> nextChange = new TransactionTemplate(transactionManager)
                    .execute(transaction -> {
                        syncOrderDeliveryStatusService.sync(
                                new DeliveryStatusChanged(1L, ORDER_ID, "ACCEPTED", Instant.now()));

                        final CompletableFuture<Void> started = new CompletableFuture<>();
                        final CompletableFuture<Void> pendingChange = CompletableFuture.runAsync(() -> {
                            started.complete(null);
                            syncOrderDeliveryStatusService.sync(
                                    new DeliveryStatusChanged(1L, ORDER_ID, "PICKED_UP", Instant.now()));
                        }, executor);

                        started.orTimeout(5, TimeUnit.SECONDS).join();
                        assertThatThrownBy(() -> pendingChange.get(300, TimeUnit.MILLISECONDS))
                                .isInstanceOf(TimeoutException.class);
                        return pendingChange;
                    });

            assertThat(nextChange).isNotNull();
            nextChange.get(5, TimeUnit.SECONDS);
            assertThat(deliveryStatusOf(ORDER_ID)).isEqualTo("PICKED_UP");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void 알_수_없는_상태는_거부된다() {
        assertThatThrownBy(() -> eventPublisher.publishEvent(
                new DeliveryStatusChanged(1L, ORDER_ID, "FLYING", Instant.now())))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void 존재하지_않는_주문이면_거부된다() {
        assertThatThrownBy(() -> eventPublisher.publishEvent(
                new DeliveryStatusChanged(1L, 999L, "ACCEPTED", Instant.now())))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ORDER_NOT_FOUND);
    }

    private String deliveryStatusOf(final long orderId) {
        return jdbcTemplate.queryForObject(
                "SELECT delivery_status FROM orders WHERE id = ?", String.class, orderId);
    }

    private void insertMember(final long memberId) {
        jdbcTemplate.update(
                """
                INSERT INTO members (id, login_id, password, role, phone_number, address, token)
                VALUES (?, ?, 'encoded-password', 'MEMBER', '010-0000-0000', '가상 주소', ?)
                """,
                memberId,
                "member" + memberId,
                "token-" + memberId
        );
    }

    private void insertListing(final long listingId, final long sellerId) {
        jdbcTemplate.update(
                """
                INSERT INTO listings (id, seller_id, title, description, price, delivery_fee, category,
                                      condition_grade, width_cm, depth_cm, height_cm, sale_status,
                                      has_purchase_request, created_at, updated_at, deleted_at)
                VALUES (?, ?, '테스트 책상', '테스트 설명', 150000, 10000, 'DESK',
                        'A', 60, 60, 70, 'AVAILABLE', true, NOW(6), NOW(6), NULL)
                """,
                listingId,
                sellerId
        );
    }

    private void insertOrder(final long orderId, final long listingId, final long buyerId, final String status) {
        jdbcTemplate.update(
                """
                INSERT INTO orders (id, listing_id, buyer_id, delivery_status, driver_id)
                VALUES (?, ?, ?, ?, NULL)
                """,
                orderId,
                listingId,
                buyerId,
                status
        );
    }
}
