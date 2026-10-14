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
import java.util.concurrent.Executors;
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
import setty.common.PaymentCompleted;
import setty.common.PaymentFailed;
import setty.platform.listing.storage.ListingImageStorage;
import setty.support.MySqlIntegrationTestSupport;

@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class PaymentConfirmTimeoutTest extends MySqlIntegrationTestSupport {

    private static final long SELLER_ID = 801L;
    private static final long BUYER_ID = 802L;
    private static final long LISTING_ID = 81L;
    private static final long ORDER_ID = 8_001L;
    private static final int PRICE = 150_000;
    private static final int DELIVERY_FEE = 10_000;
    private static final String TOSS_ORDER_ID = ORDER_ID + "_timeout";
    private static final String CONFIRM_BODY = """
            {"paymentKey":"pk-timeout","orderId":"%s","amount":%d}
            """.formatted(TOSS_ORDER_ID, PRICE + DELIVERY_FEE);

    // 승인 요청은 토스에서 처리되지만 응답이 제한 시간(1초)보다 늦다.
    private static final AtomicInteger tossConfirmRequests = new AtomicInteger();
    private static final HttpServer toss = startToss();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplicationEvents events;

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
        tossConfirmRequests.set(0);
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
        jdbcTemplate.update("DELETE FROM payments");
        jdbcTemplate.update("DELETE FROM orders");
        jdbcTemplate.update("DELETE FROM listing_images");
        jdbcTemplate.update("DELETE FROM listings");
        jdbcTemplate.update("DELETE FROM members");
    }

    @Test
    void 승인_응답이_제한_시간을_넘기면_400_결제_승인_실패이고_결제와_주문은_그대로다() throws Exception {
        final long startedAt = System.nanoTime();

        confirm().andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAYMENT_CONFIRM_FAILED"));

        assertThat((System.nanoTime() - startedAt) / 1_000_000).isLessThan(2_500);
        assertThat(tossConfirmRequests).hasValue(1);
        assertThat(paymentCount()).isZero();
        assertThat(orderStatus()).isEqualTo("PENDING");
        assertThat(listingPurchaseRequested()).isTrue();
        assertThat(events.stream(PaymentCompleted.class)).isEmpty();
        assertThat(events.stream(PaymentFailed.class)).isEmpty();
    }

    private ResultActions confirm() throws Exception {
        return mockMvc.perform(post("/api/payments/confirm")
                .header(HttpHeaders.AUTHORIZATION, "Bearer token-" + BUYER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(CONFIRM_BODY));
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

    private static HttpServer startToss() {
        try {
            final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(Executors.newCachedThreadPool());
            server.createContext("/v1/payments/confirm", exchange -> {
                exchange.getRequestBody().readAllBytes();
                tossConfirmRequests.incrementAndGet();
                sleep(3_000);
                respond(exchange, """
                        {"paymentKey":"pk-timeout","orderId":"%s","status":"DONE","totalAmount":%d,"approvedAt":"2026-10-14T12:00:00+09:00"}
                        """.formatted(TOSS_ORDER_ID, PRICE + DELIVERY_FEE));
            });
            server.start();
            return server;
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void respond(final HttpExchange exchange, final String body) throws IOException {
        final byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        try {
            exchange.sendResponseHeaders(200, bytes.length);
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
