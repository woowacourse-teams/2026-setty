package setty.payment.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;

@Timeout(20)
class TossPaymentClientRateLimitTest {

    private static final String DONE_BODY = """
            {"paymentKey":"pk","orderId":"1_a","status":"DONE","totalAmount":1000,"approvedAt":"2026-10-14T12:00:00+09:00"}
            """;
    private static final String CANCELED_BODY = """
            {"status":"CANCELED","cancels":[{"cancelAmount":1000,"canceledAt":"2026-10-14T12:00:00+09:00","transactionKey":"tx","cancelStatus":"DONE"}]}
            """;
    private static final String TOO_MANY_BODY = """
            {"code":"TOO_MANY_REQUESTS","message":"요청이 너무 많습니다"}
            """;

    private final AtomicInteger requests = new AtomicInteger();
    private final List<String> idempotencyKeys = new CopyOnWriteArrayList<>();
    private HttpServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void 승인이_한도_초과면_RetryAfter만큼_기다렸다가_같은_멱등키로_한_번_더_보낸다() {
        respondTooManyThen("/v1/payments/confirm", "0", DONE_BODY);

        assertThat(client().confirm("pk", "1_a", 1000).status()).isEqualTo("DONE");
        assertThat(requests).hasValue(2);
        assertThat(idempotencyKeys).containsExactly("pk", "pk");
    }

    @Test
    void 다시_보내도_한도_초과면_승인_실패로_끝난다() {
        respondAlwaysTooMany("/v1/payments/confirm", "0");

        assertThatThrownBy(() -> client().confirm("pk", "1_a", 1000))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_CONFIRM_FAILED));
        assertThat(requests).hasValue(2);
    }

    @Test
    void RetryAfter가_대기_한도보다_길면_기다리지_않고_승인_실패로_끝난다() {
        respondAlwaysTooMany("/v1/payments/confirm", "30");
        final long startedAt = System.nanoTime();

        assertThatThrownBy(() -> client().confirm("pk", "1_a", 1000))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_CONFIRM_FAILED));
        assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(TossRetryAfterInterceptor.MAX_WAIT);
        assertThat(requests).hasValue(1);
    }

    @Test
    void 취소가_한도_초과면_한_번_더_보내_취소한다() {
        respondTooManyThen("/v1/payments/pk/cancel", "0", CANCELED_BODY);

        final TossCancelResult result = client().cancel("pk", "주문 만료", "refund-1");

        assertThat(result).isInstanceOfSatisfying(TossCancelResult.Canceled.class,
                canceled -> assertThat(canceled.isFullyCanceled(1000)).isTrue());
        assertThat(requests).hasValue(2);
        assertThat(idempotencyKeys).containsExactly("refund-1", "refund-1");
    }

    @Test
    void 다시_보내도_한도_초과인_취소는_거절이_아니라_다음_배치에서_다시_보낸다() {
        respondAlwaysTooMany("/v1/payments/pk/cancel", "0");

        assertThat(client().cancel("pk", "주문 만료", "refund-1"))
                .isEqualTo(new TossCancelResult.Retryable("TOO_MANY_REQUESTS"));
        assertThat(requests).hasValue(2);
    }

    private void respondTooManyThen(final String path, final String retryAfter, final String successBody) {
        server.createContext(path, exchange -> {
            if (record(exchange) == 1) {
                respondTooMany(exchange, retryAfter);
                return;
            }
            respond(exchange, 200, successBody);
        });
    }

    private void respondAlwaysTooMany(final String path, final String retryAfter) {
        server.createContext(path, exchange -> {
            record(exchange);
            respondTooMany(exchange, retryAfter);
        });
    }

    private int record(final HttpExchange exchange) throws IOException {
        exchange.getRequestBody().readAllBytes();
        idempotencyKeys.add(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
        return requests.incrementAndGet();
    }

    private TossPaymentClient client() {
        final TossPaymentProperties properties = new TossPaymentProperties(
                "test_sk", "http://127.0.0.1:" + server.getAddress().getPort(),
                Duration.ofSeconds(1), Duration.ofSeconds(5));
        return new TossPaymentClient(new TossPaymentConfiguration().tossRestClient(properties));
    }

    private static void respondTooMany(final HttpExchange exchange, final String retryAfter) throws IOException {
        exchange.getResponseHeaders().add("Retry-After", retryAfter);
        respond(exchange, 429, TOO_MANY_BODY);
    }

    private static void respond(final HttpExchange exchange, final int status, final String body) throws IOException {
        final byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
