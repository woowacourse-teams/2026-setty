package setty.delivery.api;

import static java.util.stream.Collectors.joining;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import setty.delivery.application.DeliveryRequestsChanged;
import setty.delivery.domain.DriverId;

class DeliveryRequestEventStreamTest {

    private static final DriverId FIRST_DRIVER = new DriverId(1L);
    private static final DriverId SECOND_DRIVER = new DriverId(2L);
    private static final String CONNECTED_EVENT = "event:connected\ndata:{}\n\n";
    private static final String REQUESTS_CHANGED_EVENT = "event:delivery-requests-changed\ndata:{}\n\n";

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

        stream.subscribe(FIRST_DRIVER);
        stream.on(new DeliveryRequestsChanged());

        assertThat(stream.subscriberCount()).isOne();
        assertThat(stream.subscriberCount(new DriverId(1L))).isOne();
        assertThat(emitter.events).containsExactly(CONNECTED_EVENT, REQUESTS_CHANGED_EVENT);
    }

    @Test
    void multipleConnectionsAreCountedByDriverAndReceiveTheSameChangeNotification() {
        final List<CapturingSseEmitter> emitters = createStream();

        stream.subscribe(FIRST_DRIVER);
        stream.subscribe(FIRST_DRIVER);
        stream.subscribe(SECOND_DRIVER);
        stream.on(new DeliveryRequestsChanged());

        assertThat(stream.subscriberCount()).isEqualTo(3);
        assertThat(stream.subscriberCount(FIRST_DRIVER)).isEqualTo(2);
        assertThat(stream.subscriberCount(SECOND_DRIVER)).isOne();
        assertThat(stream.subscriberCount(new DriverId(3L))).isZero();
        assertThat(emitters).allSatisfy(emitter ->
                assertThat(emitter.events).containsExactly(CONNECTED_EVENT, REQUESTS_CHANGED_EVENT));
    }

    @Test
    void completionTimeoutAndErrorRemoveOnlyTheirOwnConnections() {
        final List<CapturingSseEmitter> emitters = createStream();
        stream.subscribe(FIRST_DRIVER);
        stream.subscribe(FIRST_DRIVER);
        stream.subscribe(SECOND_DRIVER);

        emitters.get(0).fireCompletion();
        assertThat(stream.subscriberCount()).isEqualTo(2);
        assertThat(stream.subscriberCount(FIRST_DRIVER)).isOne();
        assertThat(stream.subscriberCount(SECOND_DRIVER)).isOne();

        emitters.get(1).fireTimeout();
        assertThat(stream.subscriberCount()).isOne();
        assertThat(stream.subscriberCount(FIRST_DRIVER)).isZero();
        assertThat(stream.subscriberCount(SECOND_DRIVER)).isOne();

        emitters.get(2).fireError();
        assertThat(stream.subscriberCount()).isZero();
        assertThat(stream.subscriberCount(SECOND_DRIVER)).isZero();
    }

    @Test
    void repeatedCleanupDoesNotRemoveAnotherConnectionOfTheSameDriver() {
        final List<CapturingSseEmitter> emitters = createStream();
        stream.subscribe(FIRST_DRIVER);
        stream.subscribe(FIRST_DRIVER);

        emitters.get(0).fireCompletion();
        emitters.get(0).fireTimeout();
        emitters.get(0).fireError();
        stream.on(new DeliveryRequestsChanged());

        assertThat(stream.subscriberCount()).isOne();
        assertThat(stream.subscriberCount(FIRST_DRIVER)).isOne();
        assertThat(emitters.get(0).events).containsExactly(CONNECTED_EVENT);
        assertThat(emitters.get(1).events).containsExactly(CONNECTED_EVENT, REQUESTS_CHANGED_EVENT);
    }

    @Test
    void failedChangeSendRemovesOnlyTheFailedConnection() {
        final List<CapturingSseEmitter> emitters = createStream();
        stream.subscribe(FIRST_DRIVER);
        stream.subscribe(FIRST_DRIVER);
        stream.subscribe(SECOND_DRIVER);
        emitters.get(0).failWith(new IllegalStateException("전송 실패"));

        stream.on(new DeliveryRequestsChanged());
        emitters.get(0).fireCompletion();

        assertThat(stream.subscriberCount()).isEqualTo(2);
        assertThat(stream.subscriberCount(FIRST_DRIVER)).isOne();
        assertThat(stream.subscriberCount(SECOND_DRIVER)).isOne();
        assertThat(emitters.get(1).events).containsExactly(CONNECTED_EVENT, REQUESTS_CHANGED_EVENT);
        assertThat(emitters.get(2).events).containsExactly(CONNECTED_EVENT, REQUESTS_CHANGED_EVENT);
    }

    @Test
    void heartbeatIsSentToEveryDriver() {
        final List<CapturingSseEmitter> emitters = createStream();
        stream.subscribe(FIRST_DRIVER);
        stream.subscribe(SECOND_DRIVER);

        ReflectionTestUtils.invokeMethod(stream, "sendHeartbeat");

        assertThat(emitters).allSatisfy(emitter ->
                assertThat(emitter.events).containsExactly(CONNECTED_EVENT, ":heartbeat\n\n"));
    }

    @Test
    void closeCompletesEveryConnectionAndClearsDriverCounts() {
        final List<CapturingSseEmitter> emitters = createStream();
        stream.subscribe(FIRST_DRIVER);
        stream.subscribe(SECOND_DRIVER);

        stream.close();

        assertThat(emitters).allSatisfy(emitter -> assertThat(emitter.completed).isTrue());
        assertThat(stream.subscriberCount()).isZero();
        assertThat(stream.subscriberCount(FIRST_DRIVER)).isZero();
        assertThat(stream.subscriberCount(SECOND_DRIVER)).isZero();
    }

    @Test
    void failedSendRemovesDisconnectedSubscriber() {
        stream = new DeliveryRequestEventStream(FailingSseEmitter::new);

        stream.subscribe(FIRST_DRIVER);

        assertThat(stream.subscriberCount()).isZero();
        assertThat(stream.subscriberCount(FIRST_DRIVER)).isZero();
    }

    @Test
    void notificationFailureDoesNotEscapeListener() {
        final CapturingSseEmitter emitter = new CapturingSseEmitter();
        stream = new DeliveryRequestEventStream(() -> emitter);
        stream.subscribe(FIRST_DRIVER);
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

    private List<CapturingSseEmitter> createStream() {
        final List<CapturingSseEmitter> emitters = new ArrayList<>();
        stream = new DeliveryRequestEventStream(() -> {
            final CapturingSseEmitter emitter = new CapturingSseEmitter();
            emitters.add(emitter);
            return emitter;
        });
        return emitters;
    }

    private static final class CapturingSseEmitter extends SseEmitter {

        private final List<String> events = new ArrayList<>();
        private RuntimeException failure;
        private Runnable completionCallback;
        private Runnable timeoutCallback;
        private Consumer<Throwable> errorCallback;
        private boolean completed;

        void failWith(final RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public void send(final SseEventBuilder event) {
            if (failure != null) {
                throw failure;
            }
            events.add(event.build().stream().map(data -> data.getData().toString()).collect(joining()));
        }

        @Override
        public void onCompletion(final Runnable callback) {
            completionCallback = callback;
        }

        @Override
        public void onTimeout(final Runnable callback) {
            timeoutCallback = callback;
        }

        @Override
        public void onError(final Consumer<Throwable> callback) {
            errorCallback = callback;
        }

        @Override
        public void complete() {
            completed = true;
            super.complete();
        }

        void fireCompletion() {
            completionCallback.run();
        }

        void fireTimeout() {
            timeoutCallback.run();
        }

        void fireError() {
            errorCallback.accept(new IOException("연결 오류"));
        }
    }

    private static final class FailingSseEmitter extends SseEmitter {

        @Override
        public void send(final SseEventBuilder event) throws IOException {
            throw new IOException("connection closed");
        }
    }
}
