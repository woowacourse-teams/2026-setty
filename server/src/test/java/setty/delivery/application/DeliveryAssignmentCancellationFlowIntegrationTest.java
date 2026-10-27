package setty.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static setty.global.event.EventPublicationTestSupport.awaitEventsHandled;

import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import setty.common.DeliveryStatus;
import setty.common.OrderConfirmed;
import setty.delivery.domain.DeliveryId;
import setty.delivery.domain.DriverId;
import setty.delivery.persistence.DeliveryRepository;
import setty.platform.listing.storage.ListingImageStorage;
import setty.platform.order.repository.OrderRepository;
import setty.platform.order.service.OrderService;
import setty.platform.order.service.SyncOrderDeliveryStatusService;
import setty.support.MySqlIntegrationTestSupport;

// 배송·주문·매물이 이벤트로 이어지는 흐름 전체를 실제 DB와 비동기 리스너로 확인한다.
@SpringBootTest
class DeliveryAssignmentCancellationFlowIntegrationTest extends MySqlIntegrationTestSupport {

    private static final long SELLER_ID = 101L;
    private static final long BUYER_ID = 202L;
    private static final long LISTING_ID = 11L;
    private static final long ORDER_ID = 1L;
    private static final DriverId DRIVER_ID = new DriverId(301L);
    private static final DriverId OTHER_DRIVER_ID = new DriverId(302L);

    @Autowired
    private DeliveryLifecycleService deliveryLifecycleService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private SyncOrderDeliveryStatusService syncOrderDeliveryStatusService;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private OrderRepository orderRepository;

    @MockitoBean
    private ListingImageStorage listingImageStorage;

    @BeforeEach
    void setUp() {
        cleanUp();
        insertMember(SELLER_ID);
        insertMember(BUYER_ID);
        insertListing();
        insertConfirmedOrder();
    }

    @AfterEach
    void cleanUp() {
        awaitEventsHandled(jdbcTemplate);
        jdbcTemplate.update("DELETE FROM EVENT_PUBLICATION");
        jdbcTemplate.update("DELETE FROM delivery");
        jdbcTemplate.update("DELETE FROM delivery_order_decision");
        jdbcTemplate.update("DELETE FROM settlements");
        jdbcTemplate.update("DELETE FROM settlement_order");
        jdbcTemplate.update("DELETE FROM orders");
        jdbcTemplate.update("DELETE FROM listing_images");
        jdbcTemplate.update("DELETE FROM listings");
        jdbcTemplate.update("DELETE FROM members");
    }

    @Test
    void 기사가_배차를_취소하면_주문은_배송_요청으로_돌아가고_매물_예약은_유지된다() {
        final DeliveryId deliveryId = requestDelivery();
        accept(deliveryId, DRIVER_ID);
        assertThat(orderColumn("delivery_status")).isEqualTo("ACCEPTED");
        assertThat(listingColumn("sale_status")).isEqualTo("RESERVED");

        deliveryLifecycleService.cancelAssignment(deliveryId, DRIVER_ID, Instant.now());
        awaitEventsHandled(jdbcTemplate);

        assertThat(orderColumn("delivery_status")).isEqualTo("REQUESTED");
        assertThat(listingColumn("sale_status")).isEqualTo("RESERVED");
        assertThat(incompletePublicationCount()).isZero();
    }

    @Test
    void 기사_취소_뒤_구매자가_취소하면_매물이_판매중으로_돌아온다() {
        final DeliveryId deliveryId = requestDelivery();
        accept(deliveryId, DRIVER_ID);
        deliveryLifecycleService.cancelAssignment(deliveryId, DRIVER_ID, Instant.now());
        awaitEventsHandled(jdbcTemplate);

        orderService.requestCancellation(ORDER_ID, BUYER_ID);
        awaitEventsHandled(jdbcTemplate);

        assertThat(deliveryColumn("status")).isEqualTo("CANCELLED");
        assertThat(orderColumn("order_status")).isEqualTo("CANCELLED");
        assertThat(listingColumn("sale_status")).isEqualTo("AVAILABLE");
        assertThat(listingColumn("has_purchase_request")).isEqualTo(false);
        assertThat(incompletePublicationCount()).isZero();
    }

    @Test
    void 취소_뒤_다시_수락되면_주문도_배차로_간다() {
        final DeliveryId deliveryId = requestDelivery();
        accept(deliveryId, DRIVER_ID);
        deliveryLifecycleService.cancelAssignment(deliveryId, DRIVER_ID, Instant.now());
        awaitEventsHandled(jdbcTemplate);

        accept(deliveryId, OTHER_DRIVER_ID);

        assertThat(orderColumn("delivery_status")).isEqualTo("ACCEPTED");
        assertThat(listingColumn("sale_status")).isEqualTo("RESERVED");
        assertThat(incompletePublicationCount()).isZero();
    }

    // 리스너는 비동기라 "A 수락 → A 취소 → B 수락"의 주문 반영이 A 수락 → B 수락 → A 취소 순으로 올 수 있다.
    @Test
    void 새_배차의_수락이_이전_배차의_취소보다_먼저_반영돼도_주문은_새_배차를_유지한다() {
        final DeliveryId deliveryId = requestDelivery();
        accept(deliveryId, DRIVER_ID);
        final Instant firstAcceptedAt = acceptedAtInDelivery();
        // B는 잠금을 기다리기 전에 시각을 정하므로 A의 수락보다 이른 시각일 수 있다.
        final Instant secondAcceptedAt = firstAcceptedAt.minusSeconds(1);

        syncOrderDeliveryStatusService.sync(deliveryId.value(), ORDER_ID, secondAcceptedAt, DeliveryStatus.ACCEPTED);
        syncOrderDeliveryStatusService.reopen(deliveryId.value(), ORDER_ID, firstAcceptedAt, Instant.now());

        assertThat(orderColumn("delivery_status")).isEqualTo("ACCEPTED");
        assertThat(orderChangedAt()).isEqualTo(secondAcceptedAt);
    }

    @Test
    void 같은_배차_취소가_두_번_와도_한_번만_되돌린다() {
        final DeliveryId deliveryId = requestDelivery();
        accept(deliveryId, DRIVER_ID);
        final Instant acceptedAt = acceptedAtInDelivery();
        final Instant reopenedAt = Instant.parse("2026-10-27T01:00:00Z");

        syncOrderDeliveryStatusService.reopen(deliveryId.value(), ORDER_ID, acceptedAt, reopenedAt);
        syncOrderDeliveryStatusService.reopen(deliveryId.value(), ORDER_ID, acceptedAt, reopenedAt.plusSeconds(1));

        assertThat(orderColumn("delivery_status")).isEqualTo("REQUESTED");
        assertThat(orderChangedAt()).isEqualTo(reopenedAt);
    }

    private DeliveryId requestDelivery() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> eventPublisher.publishEvent(
                new OrderConfirmed(
                        ORDER_ID, "테스트 책상", "DESK",
                        "서울시 가상구 출발로 1", "서울시 가상구 도착로 2", 10_000,
                        "010-0000-0001", "010-0000-0002",
                        LISTING_ID, SELLER_ID, 150_000,
                        null, null, null, null
                )
        ));
        awaitEventsHandled(jdbcTemplate);
        return new DeliveryId(jdbcTemplate.queryForObject(
                "SELECT id FROM delivery WHERE order_id = ?", Long.class, ORDER_ID
        ));
    }

    // 실제 경로처럼 나노초까지 있는 현재 시각으로 수락해, 두 표에 저장된 시각이 같게 읽히는지도 함께 확인한다.
    private void accept(final DeliveryId deliveryId, final DriverId driverId) {
        deliveryLifecycleService.accept(deliveryId, driverId, Instant.now());
        awaitEventsHandled(jdbcTemplate);
    }

    // 시각은 JDBC로 직접 읽으면 JVM 시간대로 해석되므로 운영 코드와 같이 엔티티로 읽는다.
    private Instant acceptedAtInDelivery() {
        return deliveryRepository.findAll().getFirst().getAcceptedAt();
    }

    private Instant orderChangedAt() {
        return orderRepository.findById(ORDER_ID).orElseThrow().getDeliveryStatusChangedAt();
    }

    private long incompletePublicationCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM EVENT_PUBLICATION WHERE COMPLETION_DATE IS NULL", Long.class
        );
    }

    private Object deliveryColumn(final String column) {
        return jdbcTemplate.queryForObject(
                "SELECT " + column + " FROM delivery WHERE order_id = ?", Object.class, ORDER_ID);
    }

    private Object orderColumn(final String column) {
        return jdbcTemplate.queryForObject("SELECT " + column + " FROM orders WHERE id = ?", Object.class, ORDER_ID);
    }

    private Object listingColumn(final String column) {
        return jdbcTemplate.queryForObject(
                "SELECT " + column + " FROM listings WHERE id = ?", Object.class, LISTING_ID);
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

    private void insertListing() {
        jdbcTemplate.update(
                """
                INSERT INTO listings (id, seller_id, title, description, price, delivery_fee, category,
                                      condition_grade, width_cm, depth_cm, height_cm, sale_status,
                                      has_purchase_request, created_at, updated_at, deleted_at)
                VALUES (?, ?, '테스트 책상', '테스트 설명', 150000, 10000, 'DESK',
                        'A', 60, 60, 70, 'AVAILABLE', true, NOW(6), NOW(6), NULL)
                """,
                LISTING_ID,
                SELLER_ID
        );
    }

    private void insertConfirmedOrder() {
        jdbcTemplate.update(
                """
                INSERT INTO orders (id, listing_id, buyer_id, delivery_status, order_status)
                VALUES (?, ?, ?, 'REQUESTED', 'CONFIRMED')
                """,
                ORDER_ID,
                LISTING_ID,
                BUYER_ID
        );
    }
}
