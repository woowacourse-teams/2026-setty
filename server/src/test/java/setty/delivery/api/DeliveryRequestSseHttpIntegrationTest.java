package setty.delivery.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import setty.delivery.application.DeliveryRequestsChanged;
import setty.delivery.application.RegisterDeliveryService;
import setty.delivery.domain.DriverId;
import setty.delivery.domain.OrderId;
import setty.delivery.domain.delivery.DeliveryPoint;
import setty.delivery.domain.delivery.DeliveryRoute;
import setty.delivery.domain.delivery.EstimatedDeliveryFee;
import setty.delivery.domain.delivery.FurnitureInfo;
import setty.support.MySqlIntegrationTestSupport;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.address=127.0.0.1",
        "setty.delivery.request-sse.max-connections=3"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DeliveryRequestSseHttpIntegrationTest extends MySqlIntegrationTestSupport {

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(5);
    private static final String EVENTS_PATH = "/api/delivery/requests/events";
    private static final String REQUESTS_PATH = "/api/delivery/requests";

    @Autowired
    private Environment environment;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DeliveryRequestEventStream eventStream;

    @Autowired
    private RegisterDeliveryService registerDeliveryService;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final List<SseConnection> connections = new ArrayList<>();
    private final Map<Integer, DriverId> driverIds = new HashMap<>();
    private HttpClient client;
    private ExecutorService readers;
    private String baseUrl;

    @BeforeEach
    void setUp() {
        client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(HTTP_TIMEOUT)
                .build();
        readers = Executors.newVirtualThreadPerTaskExecutor();
        baseUrl = "http://127.0.0.1:" + environment.getRequiredProperty("local.server.port");
        assertThat(eventStream.subscriberCount()).isZero();
        jdbcTemplate.update("DELETE FROM delivery");
        jdbcTemplate.update("DELETE FROM delivery_order_decision");
        jdbcTemplate.update("DELETE FROM EVENT_PUBLICATION");
        for (int driver = 1; driver <= 4; driver++) {
            final String loginId = "sse_http_" + driver;
            jdbcTemplate.update("DELETE FROM delivery_member WHERE login_id = ?", loginId);
            jdbcTemplate.update(
                    """
                    INSERT INTO delivery_member (
                        login_id, password, phone_number, license_plate_number,
                        car_type, business_registration_number, token
                    ) VALUES (?, ?, ?, ?, ?, ?, ?)
                    """,
                    loginId, "test-password-hash", "010-0000-000" + driver, "00가000" + driver,
                    "다마스", "000-00-00000", token(driver)
            );
            driverIds.put(driver, new DriverId(jdbcTemplate.queryForObject(
                    "SELECT id FROM delivery_member WHERE token = ?", Long.class, token(driver))));
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        try {
            for (final SseConnection connection : connections) {
                connection.close();
            }
        } finally {
            if (client != null) {
                client.shutdownNow();
            }
            if (readers != null) {
                readers.shutdownNow();
            }
        }
        awaitSubscriberCount(0);
        assertThat(readers.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void authenticatedSubscriptionStartsAnSseResponseAndCanReadTheList() throws Exception {
        connect(1);

        assertThat(eventStream.subscriberCount()).isOne();
        assertThat(eventStream.subscriberCount(driverIds.get(1))).isOne();
        final HttpResponse<String> list = get(REQUESTS_PATH, 1, MediaType.APPLICATION_JSON_VALUE);
        assertThat(list.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readTree(list.body()).isArray()).isTrue();
        assertThat(objectMapper.readTree(list.body()).size()).isZero();
    }

    @Test
    void driverLimitRejectsBeforeSseAndAllowsReconnectionAfterSocketCleanup() throws Exception {
        final SseConnection first = connect(1);
        final SseConnection second = connect(1);

        assertRejected(1, 429, "DRIVER_LIMIT_EXCEEDED");
        assertThat(eventStream.subscriberCount()).isEqualTo(2);
        assertThat(eventStream.subscriberCount(driverIds.get(1))).isEqualTo(2);
        publishChange();
        assertThat(receiveEvent(first)).isEqualTo(DeliveryRequestEventStream.REQUESTS_CHANGED_EVENT);
        assertThat(receiveEvent(second)).isEqualTo(DeliveryRequestEventStream.REQUESTS_CHANGED_EVENT);

        first.close();
        awaitSubscriberCount(1);
        assertThat(eventStream.subscriberCount(driverIds.get(1))).isOne();
        final long deliveryId = registerRequest(94_001L);
        connect(1);

        assertThat(eventStream.subscriberCount(driverIds.get(1))).isEqualTo(2);
        assertLatestList(1, deliveryId);
    }

    @Test
    void totalLimitRejectsBeforeSseAndAllowsAnotherDriverAfterSocketCleanup() throws Exception {
        final SseConnection first = connect(1);
        final SseConnection second = connect(2);
        final SseConnection third = connect(3);

        assertRejected(4, 503, "TOTAL_LIMIT_EXCEEDED");
        assertThat(eventStream.subscriberCount()).isEqualTo(3);
        assertThat(eventStream.subscriberCount(driverIds.get(4))).isZero();
        publishChange();
        for (final SseConnection connection : List.of(first, second, third)) {
            assertThat(receiveEvent(connection)).isEqualTo(DeliveryRequestEventStream.REQUESTS_CHANGED_EVENT);
        }

        first.close();
        awaitSubscriberCount(2);
        assertThat(eventStream.subscriberCount(driverIds.get(1))).isZero();
        final long deliveryId = registerRequest(94_002L);
        connect(4);

        assertThat(eventStream.subscriberCount()).isEqualTo(3);
        assertThat(eventStream.subscriberCount(driverIds.get(4))).isOne();
        assertLatestList(4, deliveryId);
    }

    private SseConnection connect(final int driver) throws Exception {
        final HttpResponse<InputStream> response = client.send(
                request(EVENTS_PATH, driver, MediaType.TEXT_EVENT_STREAM_VALUE),
                HttpResponse.BodyHandlers.ofInputStream());
        final SseConnection connection = new SseConnection(response);
        connections.add(connection);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(MediaType.parseMediaType(response.headers().firstValue("Content-Type").orElseThrow())
                .isCompatibleWith(MediaType.TEXT_EVENT_STREAM)).isTrue();
        assertThat(receiveEvent(connection)).isEqualTo(DeliveryRequestEventStream.CONNECTED_EVENT);
        return connection;
    }

    private void assertRejected(final int driver, final int status, final String code) throws Exception {
        // 완결된 JSON 본문까지 제한 시간 안에 받아야 SSE 응답이 시작되지 않았음을 확인할 수 있다.
        final HttpResponse<String> response = get(EVENTS_PATH, driver, MediaType.TEXT_EVENT_STREAM_VALUE);
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(MediaType.parseMediaType(response.headers().firstValue("Content-Type").orElseThrow())
                .isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
        final JsonNode body = objectMapper.readTree(response.body());
        assertThat(body.isObject()).isTrue();
        assertThat(body.path("code").asString()).isEqualTo(code);
        assertThat(body.path("message").asString()).isNotBlank();
        assertThat(response.body()).doesNotContain("event:", "data:");
    }

    private void assertLatestList(final int driver, final long deliveryId) throws Exception {
        final HttpResponse<String> response = get(REQUESTS_PATH, driver, MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.statusCode()).isEqualTo(200);
        final JsonNode body = objectMapper.readTree(response.body());
        assertThat(body.isArray()).isTrue();
        assertThat(body.size()).isOne();
        assertThat(body.get(0).path("deliveryId").asLong()).isEqualTo(deliveryId);
    }

    private HttpResponse<String> get(final String path, final int driver, final String accept) throws Exception {
        final Future<HttpResponse<String>> response = client.sendAsync(
                request(path, driver, accept), HttpResponse.BodyHandlers.ofString());
        try {
            return response.get(HTTP_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } finally {
            response.cancel(true);
        }
    }

    private HttpRequest request(final String path, final int driver, final String accept) {
        return HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(HTTP_TIMEOUT)
                .header("Authorization", "Bearer " + token(driver))
                .header("Accept", accept)
                .GET()
                .build();
    }

    private String receiveEvent(final SseConnection connection) throws Exception {
        final Future<String> event = readers.submit(connection::readEvent);
        try {
            return event.get(HTTP_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } catch (final TimeoutException exception) {
            connection.close();
            throw exception;
        } finally {
            event.cancel(true);
        }
    }

    private void awaitSubscriberCount(final int expected) {
        // TCP 종료는 즉시 콜백으로 처리된다고 가정하지 않는다. 실제 전송이 단절을 확인하게 한다.
        await().atMost(HTTP_TIMEOUT).pollInterval(Duration.ofMillis(100)).untilAsserted(() -> {
            publishChange();
            assertThat(eventStream.subscriberCount()).isEqualTo(expected);
        });
    }

    private void publishChange() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                eventPublisher.publishEvent(new DeliveryRequestsChanged()));
    }

    private long registerRequest(final long orderId) {
        registerDeliveryService.register(
                OrderId.from(orderId),
                FurnitureInfo.of("HTTP 재접속 후 조회할 요청", "CHAIR"),
                DeliveryRoute.of(
                        DeliveryPoint.pickup("서울시 가상구 출발로 1", "010-0000-0001"),
                        DeliveryPoint.destination("서울시 가상구 도착로 2", "010-0000-0002")
                ),
                EstimatedDeliveryFee.from(10_000),
                Instant.now()
        );
        return jdbcTemplate.queryForObject("SELECT id FROM delivery WHERE order_id = ?", Long.class, orderId);
    }

    private static String token(final int driver) {
        return "sse-http-test-token-" + driver;
    }

    private static final class SseConnection implements AutoCloseable {

        private final InputStream body;
        private final BufferedReader reader;

        private SseConnection(final HttpResponse<InputStream> response) {
            body = response.body();
            reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8));
        }

        private String readEvent() throws IOException {
            String eventName = null;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() && eventName != null) {
                    return eventName;
                }
                if (line.startsWith("event:")) {
                    eventName = line.substring("event:".length()).stripLeading();
                }
            }
            throw new IOException("SSE 이벤트를 받기 전에 연결이 종료됐습니다.");
        }

        @Override
        public void close() throws IOException {
            body.close();
        }
    }
}
