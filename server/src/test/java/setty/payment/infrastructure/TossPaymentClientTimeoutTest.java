package setty.payment.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;

@Timeout(20)
class TossPaymentClientTimeoutTest {

    private static final Duration READ_TIMEOUT = Duration.ofMillis(500);
    private static final String DONE_BODY = """
            {"paymentKey":"pk","orderId":"1_a","status":"DONE","totalAmount":1000,"approvedAt":"2026-10-14T12:00:00+09:00"}
            """;

    private final CountDownLatch release = new CountDownLatch(1);
    private final AtomicInteger confirmRequests = new AtomicInteger();
    private HttpServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
    }

    @AfterEach
    void stopServer() {
        release.countDown();
        server.stop(0);
    }

    @Test
    void 응답이_제한_시간보다_늦으면_결과_확인_중으로_끝난다() {
        respondAfter(Duration.ofSeconds(3));

        assertConfirmFailsWithinTimeout();
        assertThat(confirmRequests).hasValue(1);
    }

    @Test
    void 응답이_오지_않아도_제한_시간에_결과_확인_중으로_끝난다() {
        server.createContext("/v1/payments/confirm", exchange -> {
            confirmRequests.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            await(release);
            exchange.close();
        });

        assertConfirmFailsWithinTimeout();
        assertThat(confirmRequests).hasValue(1);
    }

    // 헤더는 곧바로 오고 본문이 멈추는 경우. JDK HttpClient 자체 timeout은 헤더까지만 잰다(JDK-8258397).
    // JdkClientHttpRequestFactory의 읽기 제한이 본문 읽기까지 끊어 주는지 확인한다.
    @Test
    void 헤더만_오고_본문이_멈춰도_제한_시간에_결과_확인_중으로_끝난다() {
        server.createContext("/v1/payments/confirm", exchange -> {
            confirmRequests.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            final byte[] bytes = DONE_BODY.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes, 0, bytes.length / 2);
            exchange.getResponseBody().flush();
            await(release);
            exchange.close();
        });

        assertConfirmFailsWithinTimeout();
        assertThat(confirmRequests).hasValue(1);
    }

    @Test
    void 제한_시간_안에_온_응답은_승인된다() {
        respondAfter(Duration.ofMillis(100));

        assertThat(client().confirm("pk", "1_a", 1000).status()).isEqualTo(TossPaymentStatus.DONE);
    }

    @Test
    void 제한_시간을_설정하지_않으면_연결_3초_응답_60초다() {
        final TossPaymentProperties properties = new Binder(new MapConfigurationPropertySource(Map.of(
                "setty.payment.toss.secret-key", "test_sk",
                "setty.payment.toss.base-url", "https://api.tosspayments.com")))
                .bind("setty.payment.toss", Bindable.of(TossPaymentProperties.class))
                .get();

        assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(60));
    }

    private void assertConfirmFailsWithinTimeout() {
        final long startedAt = System.nanoTime();

        assertThatThrownBy(() -> client().confirm("pk", "1_a", 1000))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_IN_PROGRESS));
        assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(Duration.ofSeconds(2));
    }

    private void respondAfter(final Duration delay) {
        server.createContext("/v1/payments/confirm", exchange -> {
            confirmRequests.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            if (await(release, delay)) {
                exchange.close();
                return;
            }
            respond(exchange, DONE_BODY);
        });
    }

    private TossPaymentClient client() {
        final TossPaymentProperties properties = new TossPaymentProperties(
                "test_sk", "http://127.0.0.1:" + server.getAddress().getPort(), Duration.ofSeconds(1), READ_TIMEOUT);
        return new TossPaymentClient(new TossPaymentConfiguration().tossRestClient(properties));
    }

    private static void respond(final HttpExchange exchange, final String body) throws IOException {
        final byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static void await(final CountDownLatch latch) {
        await(latch, Duration.ofSeconds(30));
    }

    private static boolean await(final CountDownLatch latch, final Duration timeout) {
        try {
            return latch.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return true;
        }
    }
}
