package setty.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static setty.global.event.EventPublicationTestSupport.awaitEventsHandled;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import setty.payment.application.PaymentRefundProcessor;
import setty.payment.application.PaymentRefundService;
import setty.payment.application.PaymentService;
import setty.payment.domain.PaymentRefundReason;
import setty.platform.listing.storage.ListingImageStorage;
import setty.platform.order.service.PendingOrderExpirationService;
import setty.support.MySqlIntegrationTestSupport;

@SpringBootTest
class PaymentRefundIntegrationTest extends MySqlIntegrationTestSupport {

    private static final long SELLER_ID = 901L;
    private static final long BUYER_ID = 902L;
    private static final long LISTING_ID = 91L;
    private static final long ORDER_ID = 9_001L;
    private static final int PRICE = 150_000;
    private static final int DELIVERY_FEE = 10_000;
    private static final int AMOUNT = PRICE + DELIVERY_FEE;
    private static final String PAYMENT_KEY = "pk-refund";
    private static final String TOSS_ORDER_ID = ORDER_ID + "_refund";
    private static final int MAX_ATTEMPTS = 5;

    private static final AtomicInteger cancelRequests = new AtomicInteger();
    private static final AtomicInteger executedCancels = new AtomicInteger();
    private static final List<String> idempotencyKeys = new CopyOnWriteArrayList<>();
    private static final List<String> cancelBodies = new CopyOnWriteArrayList<>();
    private static final Set<String> executedKeys = ConcurrentHashMap.newKeySet();
    private static final Map<String, Boolean> processingKeys = new ConcurrentHashMap<>();
    private static volatile Runnable duringConfirm = () -> { };
    private static volatile Cancel cancelMode;
    private static final HttpServer toss = startToss();

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRefundProcessor paymentRefundProcessor;

    @Autowired
    private PaymentRefundService paymentRefundService;

    @Autowired
    private PendingOrderExpirationService expirationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private ListingImageStorage listingImageStorage;

    @DynamicPropertySource
    static void tossProperties(final DynamicPropertyRegistry registry) {
        registry.add("setty.payment.toss.base-url", () -> "http://127.0.0.1:" + toss.getAddress().getPort());
        registry.add("setty.payment.toss.read-timeout", () -> "1s");
        registry.add("setty.payment.recovery.max-refund-attempts", () -> MAX_ATTEMPTS);
    }

    @AfterAll
    static void stopToss() {
        toss.stop(0);
    }

    @BeforeEach
    void setUp() {
        cleanUp();
        cancelRequests.set(0);
        executedCancels.set(0);
        idempotencyKeys.clear();
        cancelBodies.clear();
        executedKeys.clear();
        processingKeys.clear();
        duringConfirm = () -> { };
        cancelMode = Cancel.DONE;
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
        jdbcTemplate.update("DELETE FROM order_notifications");
        jdbcTemplate.update("DELETE FROM delivery");
        jdbcTemplate.update("DELETE FROM delivery_order_decision");
        jdbcTemplate.update("DELETE FROM orders");
        jdbcTemplate.update("DELETE FROM listing_images");
        jdbcTemplate.update("DELETE FROM listings");
        jdbcTemplate.update("DELETE FROM members");
    }

    @Test
    void 만료_뒤_늦게_승인된_결제는_환불_대상이_되고_배치가_같은_멱등키로_전액_취소한다() {
        approveAfterOrderExpired();

        paymentRefundProcessor.processRequested();

        assertThat(refundStatus()).isEqualTo("DONE");
        assertThat(paymentStatus()).isEqualTo("CANCELED");
        assertThat(orderStatus()).isEqualTo("EXPIRED");
        assertThat(jdbcTemplate.queryForMap("SELECT transaction_key, canceled_at, attempts FROM payment_refunds"))
                .containsEntry("transaction_key", "tx-cancel")
                .containsEntry("attempts", 1)
                .extractingByKey("canceled_at").isNotNull();
        assertThat(idempotencyKeys).containsExactly(refundIdempotencyKey());
        assertThat(cancelBodies).singleElement().satisfies(body -> {
            assertThat(body).contains(PaymentRefundReason.ORDER_EXPIRED.getCancelReason());
            assertThat(body).doesNotContain("cancelAmount");
        });
    }

    @Test
    void 결제_중_판매자가_취소한_주문의_늦은_승인도_환불한다() {
        duringConfirm = () -> jdbcTemplate.update("UPDATE orders SET order_status = 'CANCELLED' WHERE id = ?", ORDER_ID);
        paymentService.confirm(TOSS_ORDER_ID, PAYMENT_KEY, AMOUNT);
        awaitEventsHandled(jdbcTemplate);

        paymentRefundProcessor.processRequested();

        assertThat(refundStatus()).isEqualTo("DONE");
        assertThat(paymentStatus()).isEqualTo("CANCELED");
        assertThat(cancelBodies).singleElement().asString()
                .contains(PaymentRefundReason.ORDER_CANCELLED.getCancelReason());
    }

    @Test
    void 결제_대기_중에_승인된_결제는_환불하지_않는다() {
        paymentService.confirm(TOSS_ORDER_ID, PAYMENT_KEY, AMOUNT);
        awaitEventsHandled(jdbcTemplate);

        paymentRefundProcessor.processRequested();

        assertThat(orderStatus()).isEqualTo("CONFIRMED");
        assertThat(refundCount()).isZero();
        assertThat(cancelRequests).hasValue(0);
    }

    @Test
    void 취소_응답이_제한_시간을_넘기면_다음_배치에서_같은_멱등키로_다시_보내_한_번만_취소된다() {
        approveAfterOrderExpired();
        cancelMode = Cancel.LATE_DONE;

        paymentRefundProcessor.processRequested();

        assertThat(refundStatus()).isEqualTo("REQUESTED");
        assertThat(lastErrorCode()).isEqualTo("NO_RESPONSE");
        assertThat(paymentStatus()).isEqualTo("DONE");

        cancelMode = Cancel.DONE;
        paymentRefundProcessor.processRequested();

        assertThat(refundStatus()).isEqualTo("DONE");
        assertThat(paymentStatus()).isEqualTo("CANCELED");
        assertThat(idempotencyKeys).hasSize(2).containsOnly(refundIdempotencyKey());
        assertThat(executedCancels).hasValue(1);
    }

    @Test
    void 일시_오류가_재시도_한도까지_이어지면_실패로_멈추고_결제는_승인_상태로_둔다() {
        approveAfterOrderExpired();
        cancelMode = Cancel.SERVER_ERROR;

        for (int attempt = 1; attempt < MAX_ATTEMPTS; attempt++) {
            paymentRefundProcessor.processRequested();
            assertThat(refundStatus()).isEqualTo("REQUESTED");
        }
        paymentRefundProcessor.processRequested();

        assertThat(refundStatus()).isEqualTo("FAILED");
        assertThat(lastErrorCode()).isEqualTo("FAILED_INTERNAL_SYSTEM_PROCESSING");
        assertThat(jdbcTemplate.queryForObject("SELECT attempts FROM payment_refunds", Integer.class))
                .isEqualTo(MAX_ATTEMPTS);
        assertThat(paymentStatus()).isEqualTo("DONE");

        paymentRefundProcessor.processRequested();
        assertThat(cancelRequests).hasValue(MAX_ATTEMPTS);
    }

    @Test
    void 처리_중이라는_응답은_다음_배치에서_다시_보낸다() {
        approveAfterOrderExpired();
        cancelMode = Cancel.CONFLICT;

        paymentRefundProcessor.processRequested();

        assertThat(refundStatus()).isEqualTo("REQUESTED");
        assertThat(lastErrorCode()).isEqualTo("IDEMPOTENT_REQUEST_PROCESSING");
    }

    // 토스 취소 에러표에서 "잠시 후 다시 시도해주세요"인 4xx. 거절이 아니라 다음 배치에서 다시 보낸다.
    @ParameterizedTest
    @CsvSource({"PROVIDER_ERROR,PROVIDER_ERROR", "CONSECUTIVE_REQUEST,FORBIDDEN_CONSECUTIVE_REQUEST"})
    void 잠시_후_다시_시도하라는_4xx는_다음_배치에서_다시_보내고_정상이_되면_완료한다(final Cancel mode, final String errorCode) {
        approveAfterOrderExpired();
        cancelMode = mode;

        paymentRefundProcessor.processRequested();

        assertThat(refundStatus()).isEqualTo("REQUESTED");
        assertThat(lastErrorCode()).isEqualTo(errorCode);
        assertThat(paymentStatus()).isEqualTo("DONE");

        cancelMode = Cancel.DONE;
        paymentRefundProcessor.processRequested();

        assertThat(refundStatus()).isEqualTo("DONE");
        assertThat(paymentStatus()).isEqualTo("CANCELED");
        assertThat(cancelRequests).hasValue(2);
    }

    @Test
    void 이미_취소된_결제라는_응답은_환불_완료로_본다() {
        approveAfterOrderExpired();
        cancelMode = Cancel.ALREADY_CANCELED;

        paymentRefundProcessor.processRequested();

        assertThat(refundStatus()).isEqualTo("DONE");
        assertThat(paymentStatus()).isEqualTo("CANCELED");
    }

    @Test
    void 취소할_수_없는_결제라는_응답은_다시_보내지_않고_실패로_멈춘다() {
        approveAfterOrderExpired();
        cancelMode = Cancel.NOT_CANCELABLE;

        paymentRefundProcessor.processRequested();
        paymentRefundProcessor.processRequested();

        assertThat(refundStatus()).isEqualTo("FAILED");
        assertThat(lastErrorCode()).isEqualTo("NOT_CANCELABLE_PAYMENT");
        assertThat(paymentStatus()).isEqualTo("DONE");
        assertThat(cancelRequests).hasValue(1);
    }

    @Test
    void 취소된_금액이_결제_금액과_다르면_완료로_보지_않는다() {
        approveAfterOrderExpired();
        cancelMode = Cancel.PARTIAL;

        paymentRefundProcessor.processRequested();

        assertThat(refundStatus()).isEqualTo("FAILED");
        assertThat(lastErrorCode()).isEqualTo("CANCEL_AMOUNT_MISMATCH");
        assertThat(paymentStatus()).isEqualTo("DONE");
    }

    @Test
    void 같은_결제의_환불_요청이_다시_와도_환불_대상은_하나다() {
        approveAfterOrderExpired();

        paymentRefundService.request(ORDER_ID, PaymentRefundReason.ORDER_EXPIRED);
        paymentRefundProcessor.processRequested();
        paymentRefundService.request(ORDER_ID, PaymentRefundReason.ORDER_EXPIRED);

        assertThat(refundCount()).isOne();
        assertThat(refundStatus()).isEqualTo("DONE");
    }

    @Test
    void 두_프로세스가_동시에_환불_배치를_돌려도_토스_취소는_한_번만_일어난다() throws Exception {
        approveAfterOrderExpired();
        cancelMode = Cancel.SLOW_DONE;
        final CountDownLatch start = new CountDownLatch(1);
        final ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            final List<Future<?>> runs = List.of(
                    executor.submit(() -> processAfter(start)),
                    executor.submit(() -> processAfter(start)));
            start.countDown();
            for (final Future<?> run : runs) {
                run.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }
        if (!"DONE".equals(refundStatus())) {
            // 처리 중 응답(409)을 받은 쪽만 기록됐으면 다음 배치에서 완료된다.
            paymentRefundProcessor.processRequested();
        }

        assertThat(refundStatus()).isEqualTo("DONE");
        assertThat(paymentStatus()).isEqualTo("CANCELED");
        assertThat(executedCancels).hasValue(1);
        assertThat(idempotencyKeys).containsOnly(refundIdempotencyKey());
    }

    private Void processAfter(final CountDownLatch start) throws InterruptedException {
        start.await();
        paymentRefundProcessor.processRequested();
        return null;
    }

    // 토스 승인 응답을 기다리는 사이 주문이 만료된 상황. 환불 대상 기록까지 기다린다.
    private void approveAfterOrderExpired() {
        duringConfirm = () -> {
            jdbcTemplate.update(
                    "UPDATE orders SET pending_expires_at = DATE_SUB(NOW(6), INTERVAL 1 MINUTE) WHERE id = ?", ORDER_ID);
            expirationService.expire(ORDER_ID, Instant.now());
        };
        paymentService.confirm(TOSS_ORDER_ID, PAYMENT_KEY, AMOUNT);
        awaitEventsHandled(jdbcTemplate);
        assertThat(refundStatus()).isEqualTo("REQUESTED");
    }

    private String refundStatus() {
        return jdbcTemplate.queryForObject("SELECT status FROM payment_refunds", String.class);
    }

    private String lastErrorCode() {
        return jdbcTemplate.queryForObject("SELECT last_error_code FROM payment_refunds", String.class);
    }

    private String refundIdempotencyKey() {
        return jdbcTemplate.queryForObject("SELECT idempotency_key FROM payment_refunds", String.class);
    }

    private int refundCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payment_refunds", Integer.class);
    }

    private String paymentStatus() {
        return jdbcTemplate.queryForObject("SELECT status FROM payments WHERE order_id = ?", String.class, ORDER_ID);
    }

    private String orderStatus() {
        return jdbcTemplate.queryForObject("SELECT order_status FROM orders WHERE id = ?", String.class, ORDER_ID);
    }

    private void insertMember(final long memberId) {
        jdbcTemplate.update("""
                INSERT INTO members (id, login_id, password, role, phone_number, address, token)
                VALUES (?, ?, 'encoded-password', 'MEMBER', '010-0000-0000', '가상 주소', ?)
                """, memberId, "member" + memberId, "token-" + memberId);
    }

    // 가짜 토스: 승인은 늘 성공하고, 취소는 cancelMode대로 응답한다.
    // 실제 토스처럼 같은 멱등키의 취소는 한 번만 실행하고(executedCancels), 처리 중인 키로 다시 오면 409를 준다.
    private enum Cancel {
        DONE, SLOW_DONE, LATE_DONE, SERVER_ERROR, CONFLICT, PROVIDER_ERROR, CONSECUTIVE_REQUEST, ALREADY_CANCELED, NOT_CANCELABLE, PARTIAL
    }

    private static HttpServer startToss() {
        try {
            final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(Executors.newCachedThreadPool());
            server.createContext("/v1/payments/confirm", exchange -> {
                exchange.getRequestBody().readAllBytes();
                duringConfirm.run();
                respond(exchange, 200, """
                        {"paymentKey":"%s","orderId":"%s","status":"DONE","totalAmount":%d,"approvedAt":"2026-11-08T12:00:00+09:00"}
                        """.formatted(PAYMENT_KEY, TOSS_ORDER_ID, AMOUNT));
            });
            server.createContext("/v1/payments/" + PAYMENT_KEY + "/cancel", PaymentRefundIntegrationTest::handleCancel);
            server.start();
            return server;
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void handleCancel(final HttpExchange exchange) throws IOException {
        cancelRequests.incrementAndGet();
        cancelBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        final String key = exchange.getRequestHeaders().getFirst("Idempotency-Key");
        idempotencyKeys.add(key);
        switch (cancelMode) {
            case DONE -> respond(exchange, 200, canceled(key, AMOUNT));
            case SLOW_DONE -> {
                if (processingKeys.putIfAbsent(key, true) != null) {
                    respond(exchange, 409, error("IDEMPOTENT_REQUEST_PROCESSING"));
                    return;
                }
                sleep(300);
                respond(exchange, 200, canceled(key, AMOUNT));
                processingKeys.remove(key);
            }
            case LATE_DONE -> {
                final String body = canceled(key, AMOUNT);
                sleep(1_500);
                respond(exchange, 200, body);
            }
            case SERVER_ERROR -> respond(exchange, 500, error("FAILED_INTERNAL_SYSTEM_PROCESSING"));
            case CONFLICT -> respond(exchange, 409, error("IDEMPOTENT_REQUEST_PROCESSING"));
            case PROVIDER_ERROR -> respond(exchange, 400, error("PROVIDER_ERROR"));
            case CONSECUTIVE_REQUEST -> respond(exchange, 403, error("FORBIDDEN_CONSECUTIVE_REQUEST"));
            case ALREADY_CANCELED -> respond(exchange, 400, error("ALREADY_CANCELED_PAYMENT"));
            case NOT_CANCELABLE -> respond(exchange, 403, error("NOT_CANCELABLE_PAYMENT"));
            case PARTIAL -> respond(exchange, 200, canceled(key, AMOUNT - 1_000));
        }
    }

    private static String canceled(final String idempotencyKey, final int canceledAmount) {
        if (executedKeys.add(idempotencyKey)) {
            executedCancels.incrementAndGet();
        }
        final String status = canceledAmount == AMOUNT ? "CANCELED" : "PARTIAL_CANCELED";
        return """
                {"paymentKey":"%s","status":"%s","totalAmount":%d,"cancels":[{"cancelAmount":%d,"canceledAt":"2026-11-08T12:05:00+09:00","transactionKey":"tx-cancel","cancelStatus":"DONE","cancelReason":"테스트"}]}
                """.formatted(PAYMENT_KEY, status, AMOUNT, canceledAmount);
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
