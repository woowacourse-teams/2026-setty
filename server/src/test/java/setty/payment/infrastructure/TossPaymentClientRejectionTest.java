package setty.payment.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;

@Timeout(20)
class TossPaymentClientRejectionTest {

    private static final String DONE_BODY = """
            {"paymentKey":"pk","orderId":"1_a","status":"DONE","totalAmount":1000,"approvedAt":"2026-10-14T12:00:00+09:00"}
            """;

    private final AtomicInteger lookupRequests = new AtomicInteger();
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

    @ParameterizedTest
    @CsvSource({
            "403, REJECT_CARD_PAYMENT, PAYMENT_METHOD_REJECTED",
            "400, INVALID_STOPPED_CARD, PAYMENT_METHOD_REJECTED",
            "403, FDS_ERROR, PAYMENT_METHOD_REJECTED",
            "404, NOT_FOUND_PAYMENT_SESSION, PAYMENT_SESSION_EXPIRED",
            "400, PROVIDER_ERROR, PAYMENT_TEMPORARILY_UNAVAILABLE",
            "400, CARD_PROCESSING_ERROR, PAYMENT_TEMPORARILY_UNAVAILABLE",
            "401, UNAUTHORIZED_KEY, PAYMENT_CONFIRM_FAILED",
            "400, INVALID_REQUEST, PAYMENT_CONFIRM_FAILED",
            "400, SOMETHING_NEW, PAYMENT_CONFIRM_FAILED"
    })
    void 토스_거절_코드를_구매자에게_안내할_승인_실패로_나눈다(final int status, final String tossCode, final ErrorCode expected) {
        respondConfirm(status, error(tossCode));

        assertRejectedWith(expected);
        assertThat(lookupRequests).hasValue(0);
    }

    @Test
    void 에러_본문을_읽을_수_없으면_일반_승인_실패로_끝난다() {
        respondConfirm(400, "<html>bad request</html>");

        assertRejectedWith(ErrorCode.PAYMENT_CONFIRM_FAILED);
    }

    @Test
    void 이미_처리_중인_요청이라는_거절은_실패로_끝내지_않고_조회로_확인한다() {
        respondConfirm(400, error("ALREADY_PROCESSING_REQUEST"));
        server.createContext("/v1/payments/pk", exchange -> {
            lookupRequests.incrementAndGet();
            respond(exchange, 200, DONE_BODY);
        });

        assertThat(client().confirm("pk", "1_a", 1000).status()).isEqualTo(TossPaymentStatus.DONE);
        assertThat(lookupRequests).hasValue(1);
    }

    private void assertRejectedWith(final ErrorCode expected) {
        assertThatThrownBy(() -> client().confirm("pk", "1_a", 1000))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    private void respondConfirm(final int status, final String body) {
        server.createContext("/v1/payments/confirm", exchange -> {
            exchange.getRequestBody().readAllBytes();
            respond(exchange, status, body);
        });
    }

    private TossPaymentClient client() {
        final TossPaymentProperties properties = new TossPaymentProperties(
                "test_sk", "http://127.0.0.1:" + server.getAddress().getPort(),
                Duration.ofSeconds(1), Duration.ofSeconds(5));
        return new TossPaymentClient(new TossPaymentConfiguration().tossRestClient(properties));
    }

    private static String error(final String code) {
        return """
                {"code":"%s","message":"가짜 토스 오류"}
                """.formatted(code);
    }

    private static void respond(final HttpExchange exchange, final int status, final String body) throws IOException {
        final byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
