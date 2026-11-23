package setty.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static setty.global.event.EventPublicationTestSupport.awaitEventsHandled;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import setty.payment.application.UnknownPaymentResolver;
import setty.platform.listing.storage.ListingImageStorage;
import setty.support.MySqlIntegrationTestSupport;

/**
 * 결과를 모르는 결제 정리 배치의 막힘(head-of-line blocking) 재현.
 *
 * <p>배치는 오래된 UNKNOWN을 attempted_at 순으로 batch-size(20)건만 가져오고, 정리에 실패해도 attempted_at을 바꾸지 않는다.
 * 그래서 계속 실패하는 UNKNOWN이 20건 쌓이면 매 배치가 그 20건만 다시 집고, 그보다 새로운 UNKNOWN은 토스에서 승인됐어도 기록되지 않는다.
 * 계속 실패하는 경우로 "토스에서는 승인됐지만 우리 기록과 금액이 다른" 결제를 쓴다. 사람이 고치기 전까지 매번 같은 결과다.
 *
 * <p>이 테스트는 현재 동작을 재현해 기록한다. 고친 뒤에는 첫 테스트의 기대를 뒤집는다.
 */
@SpringBootTest
class UnknownPaymentResolverHeadOfLineTest extends MySqlIntegrationTestSupport {

    private static final long SELLER_ID = 901L;
    private static final long BUYER_ID = 902L;
    private static final long FIRST_ID = 9_000L;
    private static final long NEW_ORDER_ID = 9_999L;
    private static final String NEW_PAYMENT_KEY = "pk-new";
    private static final int PRICE = 150_000;
    private static final int DELIVERY_FEE = 10_000;
    private static final int AMOUNT = PRICE + DELIVERY_FEE;
    private static final int BATCHES = 3;

    // 결제 키별 토스 조회 횟수
    private static final Map<String, AtomicInteger> lookups = new ConcurrentHashMap<>();
    private static final HttpServer toss = startToss();

    @Autowired
    private UnknownPaymentResolver unknownPaymentResolver;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private ListingImageStorage listingImageStorage;

    @DynamicPropertySource
    static void tossProperties(final DynamicPropertyRegistry registry) {
        registry.add("setty.payment.toss.base-url", () -> "http://127.0.0.1:" + toss.getAddress().getPort());
        registry.add("setty.payment.toss.read-timeout", () -> "1s");
    }

    @AfterAll
    static void stopToss() {
        toss.stop(0);
    }

    @BeforeEach
    void setUp() {
        cleanUp();
        lookups.clear();
        insertMember(SELLER_ID);
        insertMember(BUYER_ID);
    }

    @AfterEach
    void cleanUp() {
        awaitEventsHandled(jdbcTemplate);
        jdbcTemplate.update("DELETE FROM EVENT_PUBLICATION");
        jdbcTemplate.update("DELETE FROM payment_refunds");
        jdbcTemplate.update("DELETE FROM payments");
        jdbcTemplate.update("DELETE FROM delivery");
        jdbcTemplate.update("DELETE FROM delivery_order_decision");
        jdbcTemplate.update("DELETE FROM orders");
        jdbcTemplate.update("DELETE FROM listing_images");
        jdbcTemplate.update("DELETE FROM listings");
        jdbcTemplate.update("DELETE FROM members");
    }

    @Test
    void 계속_실패하는_결과_모름_결제가_20건이면_그보다_새로_승인된_결제는_배치를_여러_번_돌려도_기록되지_않는다() {
        leaveStuckUnknowns(20);
        leaveNewApprovedUnknown();

        for (int i = 0; i < BATCHES; i++) {
            unknownPaymentResolver.resolveStale();
        }

        assertThat(lookupCount(NEW_PAYMENT_KEY)).isZero();
        assertThat(stuckLookupTotal()).isEqualTo(20 * BATCHES);
        assertThat(paymentStatus(NEW_ORDER_ID)).isEqualTo("UNKNOWN");
        assertThat(orderStatus(NEW_ORDER_ID)).isEqualTo("PENDING");
    }

    @Test
    void 막힌_동안_새로_승인된_결제의_주문이_만료되면_환불_대상도_만들어지지_않는다() {
        leaveStuckUnknowns(20);
        leaveNewApprovedUnknown();
        jdbcTemplate.update("UPDATE orders SET order_status = 'EXPIRED' WHERE id = ?", NEW_ORDER_ID);

        for (int i = 0; i < BATCHES; i++) {
            unknownPaymentResolver.resolveStale();
        }
        awaitEventsHandled(jdbcTemplate);

        assertThat(paymentStatus(NEW_ORDER_ID)).isEqualTo("UNKNOWN");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payment_refunds", Integer.class)).isZero();
    }

    @Test
    void 막히지_않으면_만료된_주문의_승인_결제는_환불_대상이_된다() {
        leaveStuckUnknowns(19);
        leaveNewApprovedUnknown();
        jdbcTemplate.update("UPDATE orders SET order_status = 'EXPIRED' WHERE id = ?", NEW_ORDER_ID);

        unknownPaymentResolver.resolveStale();
        awaitEventsHandled(jdbcTemplate);

        assertThat(paymentStatus(NEW_ORDER_ID)).isEqualTo("DONE");
        assertThat(jdbcTemplate.queryForList("SELECT CONCAT(reason, '/', status) FROM payment_refunds", String.class))
                .containsExactly("ORDER_EXPIRED/REQUESTED");
    }

    @Test
    void 계속_실패하는_결과_모름_결제가_19건이면_새로_승인된_결제는_첫_배치에서_기록된다() {
        leaveStuckUnknowns(19);
        leaveNewApprovedUnknown();

        unknownPaymentResolver.resolveStale();
        awaitEventsHandled(jdbcTemplate);

        assertThat(lookupCount(NEW_PAYMENT_KEY)).isOne();
        assertThat(paymentStatus(NEW_ORDER_ID)).isEqualTo("DONE");
        assertThat(orderStatus(NEW_ORDER_ID)).isEqualTo("CONFIRMED");
    }

    // 토스에서는 승인됐지만 우리 기록과 금액이 달라 배치가 "수동 확인 필요" 로그만 남기고 UNKNOWN으로 두는 결제.
    // 새 결제보다 먼저 시도해 attempted_at이 더 오래됐다.
    private void leaveStuckUnknowns(final int count) {
        for (int i = 0; i < count; i++) {
            final long id = FIRST_ID + i;
            insertListingAndOrder(id);
            insertUnknownPayment(id, "pk-stuck-" + i, 60 + i);
        }
    }

    // 정리 대상이 된 지 얼마 안 된(11분 전 시도) 결제. 토스에서는 정상 승인됐다.
    private void leaveNewApprovedUnknown() {
        insertListingAndOrder(NEW_ORDER_ID);
        insertUnknownPayment(NEW_ORDER_ID, NEW_PAYMENT_KEY, 11);
    }

    private void insertListingAndOrder(final long id) {
        jdbcTemplate.update("""
                INSERT INTO listings (id, seller_id, title, description, price, delivery_fee, category,
                                      condition_grade, width_cm, depth_cm, height_cm, sale_status,
                                      has_purchase_request, created_at, updated_at, deleted_at)
                VALUES (?, ?, '테스트 책상', '테스트 설명', ?, ?, 'DESK',
                        'A', 60, 60, 70, 'AVAILABLE', true, NOW(6), NOW(6), NULL)
                """, id, SELLER_ID, PRICE, DELIVERY_FEE);
        jdbcTemplate.update("""
                INSERT INTO orders (id, listing_id, buyer_id, delivery_status, order_status, pending_expires_at)
                VALUES (?, ?, ?, 'PENDING', 'PENDING', DATE_ADD(NOW(6), INTERVAL 5 MINUTE))
                """, id, id, BUYER_ID);
    }

    private void insertUnknownPayment(final long orderId, final String paymentKey, final int minutesAgo) {
        jdbcTemplate.update("""
                INSERT INTO payments (order_id, payment_key, toss_order_id, amount, status, approved_at, attempted_at)
                VALUES (?, ?, ?, ?, 'UNKNOWN', NULL, DATE_SUB(NOW(6), INTERVAL ? MINUTE))
                """, orderId, paymentKey, tossOrderId(orderId), AMOUNT, minutesAgo);
    }

    private void insertMember(final long memberId) {
        jdbcTemplate.update("""
                INSERT INTO members (id, login_id, password, role, phone_number, address, token, token_expires_at)
                VALUES (?, ?, 'encoded-password', 'MEMBER', '010-0000-0000', '가상 주소', ?, TIMESTAMPADD(DAY, 1, UTC_TIMESTAMP(6)))
                """, memberId, "member" + memberId, "token-" + memberId);
    }

    private int lookupCount(final String paymentKey) {
        return lookups.getOrDefault(paymentKey, new AtomicInteger()).get();
    }

    private int stuckLookupTotal() {
        return lookups.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("pk-stuck-"))
                .mapToInt(entry -> entry.getValue().get())
                .sum();
    }

    private String paymentStatus(final long orderId) {
        return jdbcTemplate.queryForObject("SELECT status FROM payments WHERE order_id = ?", String.class, orderId);
    }

    private String orderStatus(final long orderId) {
        return jdbcTemplate.queryForObject("SELECT order_status FROM orders WHERE id = ?", String.class, orderId);
    }

    private static String tossOrderId(final long orderId) {
        return orderId + "_hol";
    }

    private static HttpServer startToss() {
        try {
            final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(Executors.newCachedThreadPool());
            server.createContext("/v1/payments/", exchange -> {
                final String paymentKey = exchange.getRequestURI().getPath().substring("/v1/payments/".length());
                lookups.computeIfAbsent(paymentKey, key -> new AtomicInteger()).incrementAndGet();
                if (paymentKey.equals(NEW_PAYMENT_KEY)) {
                    respond(exchange, payment(paymentKey, tossOrderId(NEW_ORDER_ID), AMOUNT));
                    return;
                }
                final long orderId = FIRST_ID + Long.parseLong(paymentKey.substring("pk-stuck-".length()));
                respond(exchange, payment(paymentKey, tossOrderId(orderId), AMOUNT - 1));
            });
            server.start();
            return server;
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String payment(final String paymentKey, final String tossOrderId, final int totalAmount) {
        return """
                {"paymentKey":"%s","orderId":"%s","status":"DONE","totalAmount":%d,"approvedAt":"2026-11-23T12:00:00+09:00"}
                """.formatted(paymentKey, tossOrderId, totalAmount);
    }

    private static void respond(final HttpExchange exchange, final String body) throws IOException {
        final byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
