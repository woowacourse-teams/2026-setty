package setty.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static setty.global.event.EventPublicationTestSupport.awaitEventsHandled;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import setty.platform.order.service.OrderPaid;
import setty.common.PaymentFailed;
import setty.payment.application.PaymentApprovalRecorder;
import setty.payment.application.UnknownPaymentResolver;
import setty.platform.listing.storage.ListingImageStorage;
import setty.platform.order.service.PendingOrderExpirationService;
import setty.support.MySqlIntegrationTestSupport;

@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class PaymentConfirmRecoveryTest extends MySqlIntegrationTestSupport {

    private static final long SELLER_ID = 801L;
    private static final long BUYER_ID = 802L;
    private static final long LISTING_ID = 81L;
    private static final long ORDER_ID = 8_001L;
    private static final int PRICE = 150_000;
    private static final int DELIVERY_FEE = 10_000;
    private static final int AMOUNT = PRICE + DELIVERY_FEE;
    private static final String PAYMENT_KEY = "pk-recovery";
    private static final String TOSS_ORDER_ID = ORDER_ID + "_recovery";
    private static final String OTHER_PAYMENT_KEY = "pk-second";
    private static final String CONFIRM_BODY = """
            {"paymentKey":"%s","orderId":"%s","amount":%d}
            """;

    private static final AtomicInteger confirmRequests = new AtomicInteger();
    private static final AtomicInteger lookupRequests = new AtomicInteger();
    private static final List<String> idempotencyKeys = new CopyOnWriteArrayList<>();
    private static volatile Confirm confirmMode;
    private static volatile Lookup lookupMode;
    private static final HttpServer toss = startToss();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplicationEvents events;

    @Autowired
    private UnknownPaymentResolver unknownPaymentResolver;

    @Autowired
    private PaymentApprovalRecorder paymentApprovalRecorder;

    @Autowired
    private PendingOrderExpirationService expirationService;

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
        confirmRequests.set(0);
        lookupRequests.set(0);
        idempotencyKeys.clear();
        insertMember(SELLER_ID);
        insertMember(BUYER_ID);
        jdbcTemplate.update("""
                INSERT INTO listings (id, seller_id, title, description, price, delivery_fee, category,
                                      condition_grade, width_cm, depth_cm, height_cm, sale_status,
                                      has_purchase_request, created_at, updated_at, deleted_at)
                VALUES (?, ?, '테스트 책상', '테스트 설명', ?, ?, 'DESK',
                        'A', 60, 60, 70, 'AVAILABLE', true, NOW(6), NOW(6), NULL)
                """, LISTING_ID, SELLER_ID, PRICE, DELIVERY_FEE);
        jdbcTemplate.update("""
                INSERT INTO orders (id, listing_id, buyer_id, delivery_status, order_status, pending_expires_at)
                VALUES (?, ?, ?, 'PENDING', 'PENDING', DATE_ADD(NOW(6), INTERVAL 5 MINUTE))
                """, ORDER_ID, LISTING_ID, BUYER_ID);
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
    void 승인_응답이_제한_시간을_넘겨도_토스에서_승인됐으면_조회로_확인해_기록한다() throws Exception {
        toss(Confirm.LATE_DONE, Lookup.DONE);

        confirm().andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"));

        assertRecordedOnce();
        assertThat(lookupRequests).hasValue(1);
        assertThat(idempotencyKeys).containsExactly(PAYMENT_KEY);
    }

    @Test
    void 제한_시간에_걸린_뒤_다시_승인하면_이미_처리된_결제를_조회로_확인해_기록한다() throws Exception {
        toss(Confirm.LATE_DONE, Lookup.FAIL);
        confirm().andExpect(status().isConflict());

        toss(Confirm.ALREADY_PROCESSED, Lookup.DONE);
        confirm().andExpect(status().isOk());

        assertRecordedOnce();
        assertThat(idempotencyKeys).containsExactly(PAYMENT_KEY, PAYMENT_KEY);
    }

    @Test
    void 토스가_5xx로_응답하면_조회로_확인해_기록한다() throws Exception {
        toss(Confirm.SERVER_ERROR, Lookup.DONE);

        confirm().andExpect(status().isOk());

        assertRecordedOnce();
    }

    @Test
    void 조회한_결제_금액이_다르면_결과_확인_중으로_답하고_시도는_결과를_모르는_채로_남긴다() throws Exception {
        toss(Confirm.ALREADY_PROCESSED, Lookup.WRONG_AMOUNT);

        confirm().andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_IN_PROGRESS"));

        assertLeftUnknown();
    }

    @Test
    void 승인_응답이_제한_시간을_넘기고_조회도_실패하면_409_결과_확인_중이고_시도는_결과를_모르는_채로_남는다() throws Exception {
        toss(Confirm.LATE_DONE, Lookup.FAIL);
        final long startedAt = System.nanoTime();

        confirm().andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_IN_PROGRESS"));

        assertThat((System.nanoTime() - startedAt) / 1_000_000).isLessThan(2_500);
        assertThat(lookupRequests).hasValue(1);
        assertLeftUnknown();
        assertThat(listingPurchaseRequested()).isTrue();

        // 클라이언트는 승인 오류를 받으면 실패 정리를 부른다. 결과를 모르는 결제는 실패가 아니므로 이벤트를 내지 않는다.
        fail().andExpect(status().isNoContent());
        assertThat(events.stream(PaymentFailed.class)).isEmpty();
        assertLeftUnknown();
    }

    @Test
    void 승인_응답이_제한_시간을_넘기고_조회가_경로_오류_404면_시도를_지우지_않는다() throws Exception {
        toss(Confirm.LATE_DONE, Lookup.PATH_NOT_FOUND);

        confirm().andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_IN_PROGRESS"));

        assertThat(lookupRequests).hasValue(1);
        assertLeftUnknown();
    }

    @Test
    void 카드_거절_같은_다른_실패는_조회하지_않는다() throws Exception {
        toss(Confirm.REJECTED, Lookup.DONE);

        confirm().andExpect(status().isBadRequest());

        assertThat(lookupRequests).hasValue(0);
        assertNotRecorded();
    }

    @Test
    void 조회로_기록한_결제를_다시_승인해도_한_번만_기록된다() throws Exception {
        toss(Confirm.ALREADY_PROCESSED, Lookup.DONE);

        confirm().andExpect(status().isOk());
        confirm().andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"));

        assertRecordedOnce();
        assertThat(confirmRequests).hasValue(1);
    }

    @Test
    void 결과를_모르는_시도가_남아_있으면_다른_결제_키의_재결제는_토스를_부르지_않고_막는다() throws Exception {
        toss(Confirm.LATE_DONE, Lookup.FAIL);
        confirm().andExpect(status().isConflict());

        confirm(OTHER_PAYMENT_KEY).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_IN_PROGRESS"));

        assertThat(confirmRequests).hasValue(1);
        assertLeftUnknown();
    }

    @Test
    void 토스가_명확히_거절하면_시도를_지워_같은_주문으로_다시_결제할_수_있다() throws Exception {
        toss(Confirm.REJECTED, Lookup.DONE);
        confirm().andExpect(status().isBadRequest());
        assertThat(paymentCount()).isZero();

        toss(Confirm.DONE, Lookup.DONE);
        confirm(OTHER_PAYMENT_KEY).andExpect(status().isOk());

        assertRecordedOnce();
    }

    @Test
    void 정리_배치는_오래된_시도를_토스에_다시_물어_승인됐으면_주문을_확정한다() throws Exception {
        leaveUnknownAttempt();
        toss(Confirm.DONE, Lookup.DONE);

        unknownPaymentResolver.resolveStale();

        assertRecordedOnce();
        assertThat(paymentStatus()).isEqualTo("DONE");
    }

    @Test
    void 정리_배치가_찾은_승인이_만료된_주문의_것이면_주문은_그대로_두고_환불_대상으로_기록한다() throws Exception {
        leaveUnknownAttempt();
        expireOrder();
        toss(Confirm.DONE, Lookup.DONE);

        unknownPaymentResolver.resolveStale();
        awaitEventsHandled(jdbcTemplate);

        assertThat(paymentStatus()).isEqualTo("DONE");
        assertThat(orderStatus()).isEqualTo("EXPIRED");
        assertThat(jdbcTemplate.queryForList("SELECT CONCAT(reason, '/', status) FROM payment_refunds", String.class))
                .containsExactly("ORDER_EXPIRED/REQUESTED");
    }

    @Test
    void 정리_배치는_토스에_없는_결제의_시도를_지운다() throws Exception {
        leaveUnknownAttempt();
        toss(Confirm.DONE, Lookup.NOT_FOUND);

        unknownPaymentResolver.resolveStale();

        assertThat(paymentCount()).isZero();
        assertThat(events.stream(OrderPaid.class)).isEmpty();
    }

    @Test
    void 정리_배치는_경로_오류_404를_받으면_시도를_남겨_다음에_다시_묻는다() throws Exception {
        leaveUnknownAttempt();
        toss(Confirm.DONE, Lookup.PATH_NOT_FOUND);

        unknownPaymentResolver.resolveStale();

        assertLeftUnknown();
    }

    @Test
    void 정리_배치는_토스가_아직_처리_중이라고_하면_시도를_남겨_다음에_다시_묻는다() throws Exception {
        leaveUnknownAttempt();
        toss(Confirm.DONE, Lookup.IN_PROGRESS);

        unknownPaymentResolver.resolveStale();

        assertLeftUnknown();
    }

    @Test
    void 정리_배치는_만든_지_얼마_안_된_시도를_조회하지_않는다() throws Exception {
        toss(Confirm.LATE_DONE, Lookup.FAIL);
        confirm().andExpect(status().isConflict());
        lookupRequests.set(0);

        unknownPaymentResolver.resolveStale();

        assertThat(lookupRequests).hasValue(0);
        assertLeftUnknown();
    }

    @Test
    void 같은_주문을_다른_결제_키로_동시에_승인하면_토스_승인은_한_번만_호출된다() throws Exception {
        toss(Confirm.SLOW_DONE, Lookup.DONE);
        final CountDownLatch start = new CountDownLatch(1);
        final ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            final Future<Integer> first = executor.submit(() -> {
                start.await();
                return confirm(PAYMENT_KEY).andReturn().getResponse().getStatus();
            });
            final Future<Integer> second = executor.submit(() -> {
                start.await();
                return confirm(OTHER_PAYMENT_KEY).andReturn().getResponse().getStatus();
            });
            start.countDown();

            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        } finally {
            executor.shutdownNow();
        }

        assertThat(confirmRequests).hasValue(1);
        assertRecordedOnce();
    }

    @Test
    void 늦은_승인_응답과_정리_배치가_같은_결제를_동시에_기록해도_주문_확정은_한_번이다() throws Exception {
        leaveUnknownAttempt();
        final CountDownLatch start = new CountDownLatch(1);
        final ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            final List<Future<?>> recorded = List.of(
                    executor.submit(() -> recordCompletedAfter(start)),
                    executor.submit(() -> recordCompletedAfter(start)));
            start.countDown();
            for (final Future<?> future : recorded) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertRecordedOnce();
    }

    private Void recordCompletedAfter(final CountDownLatch start) throws InterruptedException {
        start.await();
        paymentApprovalRecorder.record(ORDER_ID, TOSS_ORDER_ID, PAYMENT_KEY, AMOUNT, LocalDateTime.now());
        return null;
    }

    // 승인 응답이 늦고 조회도 실패해 결과를 모르는 시도가 남은 뒤, 정리 대상이 될 만큼 시간이 흐른 상황.
    private void leaveUnknownAttempt() throws Exception {
        toss(Confirm.LATE_DONE, Lookup.FAIL);
        confirm().andExpect(status().isConflict());
        jdbcTemplate.update("UPDATE payments SET attempted_at = DATE_SUB(NOW(6), INTERVAL 11 MINUTE)");
    }

    private void expireOrder() {
        jdbcTemplate.update(
                "UPDATE orders SET pending_expires_at = DATE_SUB(NOW(6), INTERVAL 1 MINUTE) WHERE id = ?", ORDER_ID);
        assertThat(expirationService.expire(ORDER_ID, Instant.now())).isTrue();
    }

    private void assertLeftUnknown() {
        assertThat(paymentCount()).isOne();
        assertThat(paymentStatus()).isEqualTo("UNKNOWN");
        assertThat(orderStatus()).isEqualTo("PENDING");
        assertThat(events.stream(OrderPaid.class)).isEmpty();
    }

    private String paymentStatus() {
        return jdbcTemplate.queryForObject("SELECT status FROM payments WHERE order_id = ?", String.class, ORDER_ID);
    }

    private void assertRecordedOnce() {
        awaitEventsHandled(jdbcTemplate);
        assertThat(paymentCount()).isOne();
        // 동시 기록은 다른 스레드에서 일어나 이벤트 기록으로 셀 수 없다. 확정마다 배송 요청이 하나 생기므로 그 수로 센다.
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM delivery WHERE order_id = ?", Integer.class, ORDER_ID)).isOne();
        assertThat(orderStatus()).isEqualTo("CONFIRMED");
    }

    private void assertNotRecorded() {
        assertThat(paymentCount()).isZero();
        assertThat(orderStatus()).isEqualTo("PENDING");
        assertThat(events.stream(OrderPaid.class)).isEmpty();
    }

    private ResultActions confirm() throws Exception {
        return confirm(PAYMENT_KEY);
    }

    private ResultActions fail() throws Exception {
        return mockMvc.perform(post("/api/payments/fail")
                .header(HttpHeaders.AUTHORIZATION, "Bearer token-" + BUYER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":\"%s\"}".formatted(TOSS_ORDER_ID)));
    }

    private ResultActions confirm(final String paymentKey) throws Exception {
        return mockMvc.perform(post("/api/payments/confirm")
                .header(HttpHeaders.AUTHORIZATION, "Bearer token-" + BUYER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(CONFIRM_BODY.formatted(paymentKey, TOSS_ORDER_ID, AMOUNT)));
    }

    private int paymentCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payments", Integer.class);
    }

    private String orderStatus() {
        return jdbcTemplate.queryForObject("SELECT order_status FROM orders WHERE id = ?", String.class, ORDER_ID);
    }

    private boolean listingPurchaseRequested() {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT has_purchase_request FROM listings WHERE id = ?", Boolean.class, LISTING_ID));
    }

    private void insertMember(final long memberId) {
        jdbcTemplate.update("""
                INSERT INTO members (id, login_id, password, role, phone_number, address, token)
                VALUES (?, ?, 'encoded-password', 'MEMBER', '010-0000-0000', '가상 주소', ?)
                """, memberId, "member" + memberId, "token-" + memberId);
    }

    private static void toss(final Confirm confirm, final Lookup lookup) {
        confirmMode = confirm;
        lookupMode = lookup;
    }

    // 가짜 토스: 승인은 confirmMode, 조회(GET /v1/payments/{paymentKey})는 lookupMode대로 응답한다.
    private enum Confirm { DONE, SLOW_DONE, LATE_DONE, ALREADY_PROCESSED, SERVER_ERROR, REJECTED }

    private enum Lookup { DONE, WRONG_AMOUNT, IN_PROGRESS, NOT_FOUND, PATH_NOT_FOUND, FAIL }

    private static HttpServer startToss() {
        try {
            final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(Executors.newCachedThreadPool());
            server.createContext("/v1/payments/confirm", exchange -> {
                exchange.getRequestBody().readAllBytes();
                confirmRequests.incrementAndGet();
                idempotencyKeys.add(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
                switch (confirmMode) {
                    case DONE -> respond(exchange, 200, payment(AMOUNT));
                    case SLOW_DONE -> {
                        sleep(300);
                        respond(exchange, 200, payment(AMOUNT));
                    }
                    case LATE_DONE -> {
                        sleep(3_000);
                        respond(exchange, 200, payment(AMOUNT));
                    }
                    case ALREADY_PROCESSED -> respond(exchange, 400, error("ALREADY_PROCESSED_PAYMENT"));
                    case SERVER_ERROR -> respond(exchange, 500, error("FAILED_INTERNAL_SYSTEM_PROCESSING"));
                    case REJECTED -> respond(exchange, 400, error("REJECT_CARD_PAYMENT"));
                }
            });
            server.createContext("/v1/payments/" + PAYMENT_KEY, exchange -> {
                lookupRequests.incrementAndGet();
                switch (lookupMode) {
                    case DONE -> respond(exchange, 200, payment(AMOUNT));
                    case WRONG_AMOUNT -> respond(exchange, 200, payment(AMOUNT - 1));
                    case IN_PROGRESS -> respond(exchange, 200, payment("IN_PROGRESS", AMOUNT));
                    case NOT_FOUND -> respond(exchange, 404, error("NOT_FOUND_PAYMENT"));
                    case PATH_NOT_FOUND -> respond(exchange, 404, error("NOT_FOUND"));
                    case FAIL -> respond(exchange, 500, error("FAILED_PAYMENT_INTERNAL_SYSTEM_PROCESSING"));
                }
            });
            server.start();
            return server;
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String payment(final int totalAmount) {
        return payment("DONE", totalAmount);
    }

    private static String payment(final String status, final int totalAmount) {
        return """
                {"paymentKey":"%s","orderId":"%s","status":"%s","totalAmount":%d,"approvedAt":"2026-10-14T12:00:00+09:00"}
                """.formatted(PAYMENT_KEY, TOSS_ORDER_ID, status, totalAmount);
    }

    private static String error(final String code) {
        return """
                {"code":"%s","message":"가짜 토스 오류"}
                """.formatted(code);
    }

    private static void respond(final HttpExchange exchange, final int status, final String body) throws IOException {
        final byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        try {
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        } catch (final IOException ignored) {
            // 제한 시간이 지나 클라이언트가 이미 연결을 끊었다.
        } finally {
            exchange.close();
        }
    }

    private static void sleep(final long millis) {
        try {
            Thread.sleep(millis);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
