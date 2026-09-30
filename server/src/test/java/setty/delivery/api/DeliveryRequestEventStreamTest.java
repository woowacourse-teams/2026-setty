package setty.delivery.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import setty.delivery.application.DeliveryRequestsChanged;

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
        stream.on(new DeliveryRequestsChanged());

        assertThat(stream.subscriberCount()).isOne();
        assertThat(emitter.events).hasSize(2);
    }

    @Test
    void failedSendRemovesDisconnectedSubscriber() {
        stream = new DeliveryRequestEventStream(FailingSseEmitter::new);

        stream.subscribe();

        assertThat(stream.subscriberCount()).isZero();
    }

    @Test
    void notificationFailureDoesNotEscapeListener() {
        final CapturingSseEmitter emitter = new CapturingSseEmitter();
        stream = new DeliveryRequestEventStream(() -> emitter);
        stream.subscribe();
        emitter.failWith(new UnsupportedOperationException("전송 실패"));

        assertThatCode(() -> stream.on(new DeliveryRequestsChanged()))
                .doesNotThrowAnyException();
    }

    @Test
    void requestNotificationRunsAfterCommit() throws NoSuchMethodException {
        final Method on = DeliveryRequestEventStream.class.getMethod("on", DeliveryRequestsChanged.class);

        final TransactionalEventListener annotation = on.getAnnotation(TransactionalEventListener.class);

        assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }

    private static final class CapturingSseEmitter extends SseEmitter {

        private final List<SseEventBuilder> events = new ArrayList<>();
        private RuntimeException failure;

        void failWith(final RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public void send(final SseEventBuilder event) {
            if (failure != null) {
                throw failure;
            }
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
