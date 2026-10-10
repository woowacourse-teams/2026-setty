package setty.notification.send;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class MockPushNotificationSenderTest {

    private HttpServer server;
    private final AtomicReference<String> responseBody = new AtomicReference<>("{\"failedNotificationIds\":[]}");
    private final AtomicReference<Integer> responseStatus = new AtomicReference<>(200);
    private final AtomicReference<String> receivedBody = new AtomicReference<>();
    private MockPushNotificationSender sender;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/send", exchange -> {
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            final byte[] body = responseBody.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(responseStatus.get(), body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        sender = new MockPushNotificationSender(new ObjectMapper(), new NotificationSendProperties(
                1, false, "http://127.0.0.1:" + server.getAddress().getPort()));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void 모의_서버가_돌려준_실패_id만_실패로_본다() {
        responseBody.set("{\"failedNotificationIds\":[2]}");

        final SendResult result = sender.send(List.of(message(1L), message(2L)));

        assertThat(result.failed(1L)).isFalse();
        assertThat(result.failed(2L)).isTrue();
        assertThat(receivedBody.get()).contains("\"notificationId\":1").contains("\"priority\":\"HIGH\"");
    }

    @Test
    void 모의_서버가_200이_아니면_전부_실패로_본다() {
        responseStatus.set(500);

        final SendResult result = sender.send(List.of(message(1L), message(2L)));

        assertThat(result.failedNotificationIds()).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void 모의_서버에_연결하지_못하면_전부_실패로_본다() {
        final MockPushNotificationSender unreachable = new MockPushNotificationSender(
                new ObjectMapper(), new NotificationSendProperties(1, false, "http://127.0.0.1:1"));

        final SendResult result = unreachable.send(List.of(message(1L)));

        assertThat(result.failed(1L)).isTrue();
    }

    private static NotificationMessage message(final Long id) {
        return new NotificationMessage(id, 100L + id, 7L, "s3-notify desk", NotificationMessage.Priority.HIGH);
    }
}
