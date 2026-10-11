package setty.platform.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static setty.global.event.EventPublicationTestSupport.awaitEventsHandled;

import java.time.Duration;
import java.time.Instant;
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
import setty.common.DeliveryStatus;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;
import setty.platform.order.service.OrderCompletionScheduler;
import setty.platform.order.service.OrderCompletionService;
import setty.platform.order.service.SyncOrderDeliveryStatusService;
import setty.support.MySqlIntegrationTestSupport;

@SpringBootTest
class OrderCompletionIntegrationTest extends MySqlIntegrationTestSupport {

    private static final long SELLER_ID = 101L;
    private static final long BUYER_ID = 202L;
    private static final long OTHER_BUYER_ID = 203L;
    private static final long LISTING_ID = 11L;
    private static final long OTHER_LISTING_ID = 12L;
    private static final long ORDER_ID = 1L;
    private static final long OTHER_ORDER_ID = 2L;

    @Autowired
    private OrderCompletionService orderCompletionService;

    @Autowired
    private OrderCompletionScheduler orderCompletionScheduler;

    @Autowired
    private SyncOrderDeliveryStatusService syncOrderDeliveryStatusService;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        cleanUp();
        insertMember(SELLER_ID);
        insertMember(BUYER_ID);
        insertMember(OTHER_BUYER_ID);
        insertListing(LISTING_ID);
        insertListing(OTHER_LISTING_ID);
        insertConfirmedOrder(ORDER_ID, LISTING_ID);
        insertConfirmedOrder(OTHER_ORDER_ID, OTHER_LISTING_ID);
    }

    @AfterEach
    void cleanUp() {
        awaitEventsHandled(jdbcTemplate);
        jdbcTemplate.update("DELETE FROM EVENT_PUBLICATION");
        jdbcTemplate.update("DELETE FROM settlements");
        jdbcTemplate.update("DELETE FROM settlement_order");
        jdbcTemplate.update("DELETE FROM orders");
        jdbcTemplate.update("DELETE FROM listings");
        jdbcTemplate.update("DELETE FROM members");
    }

    @Test
    void 배송_완료만으로는_판매_완료되지_않는다() {
        pickUp(ORDER_ID);

        publishCommitted(new DeliveryDelivered(1L, ORDER_ID, Instant.now(), 301L, 10_000));

        assertThat(column(ORDER_ID, "delivery_status")).isEqualTo("DELIVERED");
        assertThat(column(ORDER_ID, "order_status")).isEqualTo("CONFIRMED");
        assertThat(column(ORDER_ID, "delivery_status_changed_at")).isNotNull();
        assertThat(saleStatusOf(LISTING_ID)).isEqualTo("RESERVED");
    }

    @Test
    void 구매자가_확인하면_판매_완료되고_매물과_정산이_확정된다() {
        deliver(ORDER_ID, Instant.now());

        orderCompletionService.confirmByBuyer(ORDER_ID, BUYER_ID);
        awaitEventsHandled(jdbcTemplate);

        assertThat(column(ORDER_ID, "order_status")).isEqualTo("COMPLETED");
        assertThat(saleStatusOf(LISTING_ID)).isEqualTo("SOLD");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT outcome FROM settlement_order WHERE order_id = ?", String.class, ORDER_ID
        )).isEqualTo("CONFIRMED");
    }

    @Test
    void 이미_판매_완료된_주문을_다시_확인해도_성공한다() {
        deliver(ORDER_ID, Instant.now());
        orderCompletionService.confirmByBuyer(ORDER_ID, BUYER_ID);

        orderCompletionService.confirmByBuyer(ORDER_ID, BUYER_ID);

        assertThat(column(ORDER_ID, "order_status")).isEqualTo("COMPLETED");
    }

    @Test
    void 다른_구매자의_주문은_확인할_수_없다() {
        deliver(ORDER_ID, Instant.now());

        assertThatThrownBy(() -> orderCompletionService.confirmByBuyer(ORDER_ID, OTHER_BUYER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    void 배송_완료_전에는_확인할_수_없다() {
        pickUp(ORDER_ID);

        assertThatThrownBy(() -> orderCompletionService.confirmByBuyer(ORDER_ID, BUYER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_ORDER_STATUS_TRANSITION);
        assertThat(column(ORDER_ID, "order_status")).isEqualTo("CONFIRMED");
    }

    @Test
    void 배송_완료_후_3일이_지난_주문만_자동으로_판매_완료된다() {
        deliver(ORDER_ID, Instant.now().minus(Duration.ofDays(3)).minusSeconds(60));
        deliver(OTHER_ORDER_ID, Instant.now());

        orderCompletionScheduler.completeDueOrders();
        awaitEventsHandled(jdbcTemplate);

        assertThat(column(ORDER_ID, "order_status")).isEqualTo("COMPLETED");
        assertThat(saleStatusOf(LISTING_ID)).isEqualTo("SOLD");
        assertThat(column(OTHER_ORDER_ID, "order_status")).isEqualTo("CONFIRMED");
        assertThat(saleStatusOf(OTHER_LISTING_ID)).isEqualTo("RESERVED");
    }

    private void pickUp(final long orderId) {
        syncOrderDeliveryStatusService.sync(1L, orderId, Instant.now(), DeliveryStatus.ACCEPTED);
        syncOrderDeliveryStatusService.sync(1L, orderId, Instant.now(), DeliveryStatus.PICKED_UP);
    }

    private void deliver(final long orderId, final Instant deliveredAt) {
        pickUp(orderId);
        syncOrderDeliveryStatusService.sync(1L, orderId, deliveredAt, DeliveryStatus.DELIVERED);
    }

    private void publishCommitted(final Object event) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> eventPublisher.publishEvent(event));
        awaitEventsHandled(jdbcTemplate);
    }

    private Object column(final long orderId, final String column) {
        return jdbcTemplate.queryForObject("SELECT " + column + " FROM orders WHERE id = ?", Object.class, orderId);
    }

    private String saleStatusOf(final long listingId) {
        return jdbcTemplate.queryForObject("SELECT sale_status FROM listings WHERE id = ?", String.class, listingId);
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

    private void insertListing(final long listingId) {
        jdbcTemplate.update(
                """
                INSERT INTO listings (id, seller_id, title, description, price, delivery_fee, category,
                                      condition_grade, width_cm, depth_cm, height_cm, sale_status,
                                      has_purchase_request, created_at, updated_at, deleted_at)
                VALUES (?, ?, '테스트 책상', '테스트 설명', 150000, 10000, 'DESK',
                        'A', 60, 60, 70, 'RESERVED', true, NOW(6), NOW(6), NULL)
                """,
                listingId,
                SELLER_ID
        );
    }

    private void insertConfirmedOrder(final long orderId, final long listingId) {
        jdbcTemplate.update(
                """
                INSERT INTO orders (id, listing_id, buyer_id, delivery_status, order_status)
                VALUES (?, ?, ?, 'REQUESTED', 'CONFIRMED')
                """,
                orderId,
                listingId,
                BUYER_ID
        );
    }
}
