package setty.delivery.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class DeliveryRequestEventStreamTest {

    private DeliveryRequestEventStream stream;

    @AfterEach
    void tearDown() {
        if (stream != null) {
            stream.close();
        }
    }

    @Test
    void subscriptionReceivesConnectionAndRequestChangedEvents() {
        final CapturingSseEmitter emitter = new CapturingSseEmitter();
        stream = new DeliveryRequestEventStream(() -> emitter);

        stream.subscribe();
        stream.notifyRequestsChanged();

        assertThat(stream.subscriberCount()).isOne();
        assertThat(emitter.events).hasSize(2);
    }

    @Test
    void failedSendRemovesDisconnectedSubscriber() {
        stream = new DeliveryRequestEventStream(FailingSseEmitter::new);

        stream.subscribe();

        assertThat(stream.subscriberCount()).isZero();
    }

    private static final class CapturingSseEmitter extends SseEmitter {

        private final List<SseEventBuilder> events = new ArrayList<>();

        @Override
        public void send(final SseEventBuilder event) {
            events.add(event);
        }
    }

    private static final class FailingSseEmitter extends SseEmitter {

        @Override
        public void send(final SseEventBuilder event) throws IOException {
            throw new IOException("connection closed");
        }
    }
}
