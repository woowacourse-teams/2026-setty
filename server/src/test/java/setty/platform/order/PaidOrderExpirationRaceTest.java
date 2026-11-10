package setty.platform.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static setty.global.event.EventPublicationTestSupport.awaitEventsHandled;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.config.StalenessProperties;
import org.springframework.modulith.events.core.EventPublicationRegistry;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import setty.global.event.FailedEventResubmissionScheduler;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;
import setty.payment.application.PaymentRecorder;
import setty.payment.application.PaymentService;
import setty.payment.application.UnknownPaymentResolver;
import setty.platform.PaymentAcceptance;
import setty.platform.PaymentOrderAcceptance;
import setty.platform.listing.storage.ListingImageStorage;
import setty.platform.order.service.OrderPaid;
import setty.platform.order.service.OrderService;
import setty.platform.order.service.PaidOrderConfirmationScheduler;
import setty.platform.order.service.PendingOrderExpirationScheduler;
import setty.platform.order.service.PendingOrderExpirationService;
import setty.support.MySqlIntegrationTestSupport;

/**
 * 승인을 확인한 결제의 주문이 만료와 경합하는 장면을 재현한다.
 * 주문이 먼저 결과(PAID 또는 만료)를 커밋하므로, 그 뒤 어느 지점에서 중단돼도 승인을 받아들인 주문은 만료되지 않는다.
 * 실제 프로세스를 종료하지 않고, 종료 뒤 DB에 남는 상태와 경과시간을 만든다.
 */
@SpringBootTest
class PaidOrderExpirationRaceTest extends MySqlIntegrationTestSupport {

    private static final long SELLER_ID = 951L;
    private static final long BUYER_ID = 952L;
    private static final long LISTING_ID = 95L;
    private static final long ORDER_ID = 9_501L;
    private static final int PRICE = 150_000;
    private static final int DELIVERY_FEE = 10_000;
    private static final int AMOUNT = PRICE + DELIVERY_FEE;
    private static final String PAYMENT_KEY = "pk-expiration-race";
    private static final String TOSS_ORDER_ID = ORDER_ID + "_race";

    private static final HttpServer toss = startToss();

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRecorder paymentRecorder;

    @Autowired
    private UnknownPaymentResolver unknownPaymentResolver;

    @Autowired
    private PaymentOrderAcceptance paymentOrderAcceptance;

    @Autowired
    private PendingOrderExpirationScheduler expirationScheduler;

    @Autowired
    private PendingOrderExpirationService expirationService;

    @Autowired
    private PaidOrderConfirmationScheduler paidOrderConfirmationScheduler;

    @Autowired
    private FailedEventResubmissionScheduler resubmissionScheduler;

    @Autowired
    private EventPublicationRegistry eventPublicationRegistry;

    @Autowired
    private StalenessProperties stalenessProperties;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${spring.modulith.events.staleness.processing}")
    private Duration processingStaleness;

    @MockitoSpyBean
    private OrderService orderService;

    @MockitoBean
    private ListingImageStorage listingImageStorage;

    @DynamicPropertySource
    static void tossProperties(final DynamicPropertyRegistry registry) {
        registry.add("setty.payment.toss.base-url", () -> "http://127.0.0.1:" + toss.getAddress().getPort());
    }

    @AfterAll
    static void stopToss() {
        toss.stop(0);
    }

    @BeforeEach
    @AfterEach
    void cleanUp() {
        awaitEventsHandled(jdbcTemplate);
        jdbcTemplate.update("DELETE FROM EVENT_PUBLICATION");
        jdbcTemplate.update("DELETE FROM payment_refunds");
        jdbcTemplate.update("DELETE FROM payments");
        jdbcTemplate.update("DELETE FROM order_notifications");
        jdbcTemplate.update("DELETE FROM delivery");
        jdbcTemplate.update("DELETE FROM delivery_order_decision");
        jdbcTemplate.update("DELETE FROM orders");
        jdbcTemplate.update("DELETE FROM listing_images");
        jdbcTemplate.update("DELETE FROM listings");
        jdbcTemplate.update("DELETE FROM members");
    }

    // 주문 만료 기본값은 6분이다. 결제 승인 시점에 만료까지 남은 시간(초)을 바꿔 가며 본다.
    @ParameterizedTest(name = "결제 시 만료까지 {0}초 남은 주문")
    @ValueSource(ints = {60, 180, 300})
    void 확정_처리가_중단돼도_승인을_받아들인_주문은_만료되지_않고_복구_뒤_확정된다(final int secondsLeftAtPayment) {
        insertListingAndMembers();
        insertOrder("PENDING", secondsLeftAtPayment);
        // 주문 확정 리스너가 돌던 중 JVM이 내려간 상황을 첫 처리 실패로 대신한다.
        doThrow(new IllegalStateException("주문 확정 중 프로세스가 종료됐다고 가정한다"))
                .doCallRealMethod()
                .when(orderService).confirmPaid(ORDER_ID);

        paymentService.confirm(TOSS_ORDER_ID, PAYMENT_KEY, AMOUNT);
        awaitEventsHandled(jdbcTemplate);
        assertThat(paymentStatus()).isEqualTo("DONE");
        assertThat(orderStatus()).isEqualTo("PAID");

        // 감시 기준만큼 시간이 흐른 시점으로 옮긴다. 그사이 주문의 결제 만료 시각은 지난다.
        elapse(processingStaleness);
        expirationScheduler.expirePendingOrders();
        assertThat(orderStatus()).isEqualTo("PAID");

        eventPublicationRegistry.markStalePublicationsFailed(stalenessProperties);
        resubmissionScheduler.resubmitFailedPublications();
        awaitEventsHandled(jdbcTemplate);

        assertThat(orderStatus()).isEqualTo("CONFIRMED");
        assertThat(paymentStatus()).isEqualTo("DONE");
        assertThat(refundCount()).isZero();
    }

    @Test
    void 주문_결정_뒤_결제_기록_전에_중단되면_주문은_만료되지_않고_정리_배치가_결제를_기록한다() {
        insertListingAndMembers();
        insertOrder("PENDING", 120);
        paymentRecorder.recordAttempt(ORDER_ID, TOSS_ORDER_ID, PAYMENT_KEY, AMOUNT);
        // 토스 승인 뒤 주문은 결정을 커밋했고, 결제를 기록하기 전에 프로세스가 내려갔다.
        assertThat(paymentOrderAcceptance.accept(ORDER_ID, Instant.now()).accepted()).isTrue();
        assertThat(paymentStatus()).isEqualTo("UNKNOWN");
        // 구매자가 같은 결제를 다시 요청해도 실패가 아니라 결과 확인 중으로 답한다.
        assertThatThrownBy(() -> paymentService.confirm(TOSS_ORDER_ID, PAYMENT_KEY, AMOUNT))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_IN_PROGRESS);

        // 정리 배치가 이 시도를 집을 만큼 시간이 흐른다. 주문의 결제 만료 시각도 지난다.
        jdbcTemplate.update("UPDATE payments SET attempted_at = DATE_SUB(NOW(6), INTERVAL 11 MINUTE)");
        jdbcTemplate.update(
                "UPDATE orders SET pending_expires_at = DATE_SUB(NOW(6), INTERVAL 5 MINUTE) WHERE id = ?", ORDER_ID);
        expirationScheduler.expirePendingOrders();
        assertThat(orderStatus()).isIn("PAID", "CONFIRMED");

        unknownPaymentResolver.resolveStale();
        awaitEventsHandled(jdbcTemplate);

        assertThat(paymentStatus()).isEqualTo("DONE");
        assertThat(orderStatus()).isEqualTo("CONFIRMED");
        assertThat(refundCount()).isZero();
    }

    // 승인을 받아들이는 쪽과 만료가 같은 주문을 동시에 잡아도 결과는 "받아들임" 또는 "만료" 하나다.
    @RepeatedTest(10)
    void 만료와_승인_결정이_동시에_와도_먼저_잠근_쪽의_결과만_남는다() throws Exception {
        cleanUp();
        insertListingAndMembers();
        insertOrder("PENDING", 120);
        final Instant afterDeadline = Instant.now().plus(Duration.ofMinutes(10));
        final CountDownLatch start = new CountDownLatch(1);
        final ExecutorService executor = Executors.newFixedThreadPool(2);
        final PaymentAcceptance acceptance;
        final boolean expired;
        try {
            final Future<PaymentAcceptance> accepting = executor.submit(() -> {
                start.await();
                return paymentOrderAcceptance.accept(ORDER_ID, Instant.now());
            });
            final Future<Boolean> expiring = executor.submit(() -> {
                start.await();
                return expirationService.expire(ORDER_ID, afterDeadline);
            });
            start.countDown();
            acceptance = accepting.get(10, TimeUnit.SECONDS);
            expired = expiring.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
        awaitEventsHandled(jdbcTemplate);

        assertThat(acceptance.accepted()).isNotEqualTo(expired);
        if (acceptance.accepted()) {
            assertThat(orderStatus()).isEqualTo("CONFIRMED");
        } else {
            assertThat(acceptance.orderStatus()).isEqualTo("EXPIRED");
            assertThat(orderStatus()).isEqualTo("EXPIRED");
        }
    }

    @Test
    void 만료_시각_전에_확인한_승인은_잠금을_기다리다_기한이_지나도_받아들인다() {
        insertListingAndMembers();
        insertOrder("PENDING", -1);
        final Instant confirmedBeforeDeadline = Instant.now().minusSeconds(5);

        final PaymentAcceptance acceptance = paymentOrderAcceptance.accept(ORDER_ID, confirmedBeforeDeadline);

        assertThat(acceptance.accepted()).isTrue();
        assertThat(orderStatus()).isIn("PAID", "CONFIRMED");
    }

    @Test
    void 받아들인_뒤_구매자_취소로_끝난_주문은_다시_확인해도_처음_결정을_유지한다() {
        insertListingAndMembers();
        insertOrder("PENDING", 120);
        assertThat(paymentOrderAcceptance.accept(ORDER_ID, Instant.now()).accepted()).isTrue();
        awaitEventsHandled(jdbcTemplate);
        // 결제를 기록하기 전에 중단된 사이 확정과 구매자 취소가 끝났다.
        jdbcTemplate.update(
                "UPDATE orders SET order_status = 'CANCELLED', cancellation_request_id = 'cancel-1' WHERE id = ?",
                ORDER_ID);

        assertThat(paymentOrderAcceptance.accept(ORDER_ID, Instant.now()).accepted()).isTrue();
        assertThat(orderStatus()).isEqualTo("CANCELLED");
    }

    @Test
    void 확정되지_못하고_PAID에_머문_주문은_재확정_배치가_확정한다() {
        insertListingAndMembers();
        // PAID는 만료 시각 전에만 되므로, 만료 시각이 20분 지난 PAID는 적어도 20분 머물렀다.
        insertOrder("PAID", -20 * 60);

        paidOrderConfirmationScheduler.confirmStuckPaidOrders();
        awaitEventsHandled(jdbcTemplate);

        assertThat(orderStatus()).isEqualTo("CONFIRMED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM delivery WHERE order_id = ?", Integer.class, ORDER_ID)).isOne();
    }

    @Test
    void 재확정_배치는_아직_기준_시간이_지나지_않은_PAID를_건드리지_않는다() {
        insertListingAndMembers();
        insertOrder("PAID", 60);

        paidOrderConfirmationScheduler.confirmStuckPaidOrders();

        assertThat(orderStatus()).isEqualTo("PAID");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM EVENT_PUBLICATION WHERE EVENT_TYPE = ?", Integer.class,
                OrderPaid.class.getName())).isZero();
    }

    private void elapse(final Duration elapsed) {
        final long micros = elapsed.toNanos() / 1_000;
        final int interrupted = jdbcTemplate.update(
                """
                UPDATE EVENT_PUBLICATION
                SET STATUS = 'PROCESSING',
                    PUBLICATION_DATE = DATE_SUB(PUBLICATION_DATE, INTERVAL ? MICROSECOND),
                    LAST_RESUBMISSION_DATE = NULL
                WHERE EVENT_TYPE = ? AND COMPLETION_DATE IS NULL
                """,
                micros, OrderPaid.class.getName());
        assertThat(interrupted).isEqualTo(1);
        jdbcTemplate.update(
                "UPDATE orders SET pending_expires_at = DATE_SUB(pending_expires_at, INTERVAL ? MICROSECOND) WHERE id = ?",
                micros, ORDER_ID);
    }

    private void insertListingAndMembers() {
        insertMember(SELLER_ID);
        insertMember(BUYER_ID);
        jdbcTemplate.update("""
                INSERT INTO listings (id, seller_id, title, description, price, delivery_fee, category,
                                      condition_grade, width_cm, depth_cm, height_cm, sale_status,
                                      has_purchase_request, created_at, updated_at, deleted_at)
                VALUES (?, ?, '테스트 책상', '테스트 설명', ?, ?, 'DESK',
                        'A', 60, 60, 70, 'AVAILABLE', true, NOW(6), NOW(6), NULL)
                """, LISTING_ID, SELLER_ID, PRICE, DELIVERY_FEE);
    }

    private void insertOrder(final String orderStatus, final int secondsLeft) {
        jdbcTemplate.update("""
                INSERT INTO orders (id, listing_id, buyer_id, delivery_status, order_status, pending_expires_at)
                VALUES (?, ?, ?, 'PENDING', ?, DATE_ADD(NOW(6), INTERVAL ? SECOND))
                """, ORDER_ID, LISTING_ID, BUYER_ID, orderStatus, secondsLeft);
    }

    private void insertMember(final long memberId) {
        jdbcTemplate.update("""
                INSERT INTO members (id, login_id, password, role, phone_number, address, token)
                VALUES (?, ?, 'encoded-password', 'MEMBER', '010-0000-0000', '가상 주소', ?)
                """, memberId, "member" + memberId, "token-" + memberId);
    }

    private String orderStatus() {
        return jdbcTemplate.queryForObject("SELECT order_status FROM orders WHERE id = ?", String.class, ORDER_ID);
    }

    private String paymentStatus() {
        return jdbcTemplate.queryForObject("SELECT status FROM payments WHERE order_id = ?", String.class, ORDER_ID);
    }

    private int refundCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payment_refunds", Integer.class);
    }

    // 가짜 토스: 승인과 조회 모두 승인 완료로 답한다.
    private static HttpServer startToss() {
        try {
            final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v1/payments/", PaidOrderExpirationRaceTest::respondDone);
            server.start();
            return server;
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void respondDone(final HttpExchange exchange) throws IOException {
        exchange.getRequestBody().readAllBytes();
        final byte[] body = """
                {"paymentKey":"%s","orderId":"%s","status":"DONE","totalAmount":%d,"approvedAt":"2026-11-10T12:00:00+09:00"}
                """.formatted(PAYMENT_KEY, TOSS_ORDER_ID, AMOUNT).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
