package setty.delivery.api;

import static java.util.stream.Collectors.joining;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import setty.delivery.application.DeliveryRequestsChanged;
import setty.delivery.domain.DriverId;

class DeliveryRequestEventStreamTest {

    private static final DriverId FIRST_DRIVER = new DriverId(1L);
    private static final DriverId SECOND_DRIVER = new DriverId(2L);
    private static final int MAX_TOTAL_CONNECTIONS = 3;
    private static final String CONNECTED_EVENT = "event:connected\ndata:{}\n\n";
    private static final String REQUESTS_CHANGED_EVENT = "event:delivery-requests-changed\ndata:{}\n\n";

    private final MockClock clock = new MockClock();
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock);
    private DeliveryRequestEventStream stream;

    @AfterEach
    void tearDown() {
        if (stream != null) {
            stream.close();
        }
        meterRegistry.close();
    }

    @Test
    void subscriptionReceivesConnectionAndRequestChangedEvents() {
        final CapturingSseEmitter emitter = new CapturingSseEmitter();
        stream = new DeliveryRequestEventStream(MAX_TOTAL_CONNECTIONS, () -> emitter, meterRegistry);

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
    void thirdConnectionOfTheSameDriverIsRejectedWithoutChangingExistingConnections() {
        final List<CapturingSseEmitter> emitters = createStream();
        stream.subscribe(FIRST_DRIVER);
        stream.subscribe(FIRST_DRIVER);

        assertRejected(FIRST_DRIVER, DeliveryRequestSseRejectedException.Reason.DRIVER_LIMIT_EXCEEDED);

        assertThat(stream.subscriberCount()).isEqualTo(2);
        assertThat(stream.subscriberCount(FIRST_DRIVER)).isEqualTo(2);
        assertThat(emitters.get(2).events).isEmpty();
        emitters.get(2).fireCompletion();
        stream.on(new DeliveryRequestsChanged());
        assertThat(stream.subscriberCount()).isEqualTo(2);
        assertThat(emitters.subList(0, 2)).allSatisfy(emitter ->
                assertThat(emitter.events).containsExactly(CONNECTED_EVENT, REQUESTS_CHANGED_EVENT));
    }

    @Test
    void totalLimitIncludesConnectionsOfAllDrivers() {
        createStream();
        stream.subscribe(FIRST_DRIVER);
        stream.subscribe(FIRST_DRIVER);
        stream.subscribe(SECOND_DRIVER);

        assertRejected(new DriverId(3L), DeliveryRequestSseRejectedException.Reason.TOTAL_LIMIT_EXCEEDED);

        assertThat(stream.subscriberCount()).isEqualTo(MAX_TOTAL_CONNECTIONS);
        assertThat(stream.subscriberCount(FIRST_DRIVER)).isEqualTo(2);
        assertThat(stream.subscriberCount(SECOND_DRIVER)).isOne();
    }

    @ParameterizedTest
    @EnumSource(Cleanup.class)
    void cleanupRestoresOneSlotAndRepeatedCallbacksDoNotReleaseAnotherConnection(final Cleanup cleanup) {
        final List<CapturingSseEmitter> emitters = createStream();
        stream.subscribe(FIRST_DRIVER);
        stream.subscribe(FIRST_DRIVER);
        stream.subscribe(SECOND_DRIVER);
        final CapturingSseEmitter removed = emitters.getFirst();

        switch (cleanup) {
            case COMPLETION -> removed.fireCompletion();
            case TIMEOUT -> removed.fireTimeout();
            case ERROR -> removed.fireError();
            case SEND_FAILURE -> {
                removed.failWith(new IllegalStateException("전송 실패"));
                stream.on(new DeliveryRequestsChanged());
            }
        }
        removed.fireCompletion();
        removed.fireTimeout();
        removed.fireError();
        assertThat(stream.subscriberCount()).isEqualTo(2);
        assertThat(stream.subscriberCount(FIRST_DRIVER)).isOne();
        assertThat(activeSubscriptions()).isEqualTo(2);
        assertThat(totalRemovals()).isOne();
        assertThat(removalCount(DeliveryRequestEventStream.RemovalReason.valueOf(cleanup.name()))).isOne();

        stream.subscribe(FIRST_DRIVER);
        removed.fireCompletion();
        assertThat(stream.subscriberCount()).isEqualTo(MAX_TOTAL_CONNECTIONS);
        assertThat(stream.subscriberCount(FIRST_DRIVER)).isEqualTo(2);
        assertRejected(new DriverId(3L), DeliveryRequestSseRejectedException.Reason.TOTAL_LIMIT_EXCEEDED);
        assertThat(activeSubscriptions()).isEqualTo(MAX_TOTAL_CONNECTIONS);
        assertThat(totalRemovals()).isOne();
    }

    @Test
    void concurrentSubscriptionsOfTheSameDriverNeverExceedTwoConnections() throws Exception {
        createStream();
        final List<DriverId> drivers = IntStream.range(0, 20).mapToObj(ignored -> FIRST_DRIVER).toList();

        final List<Boolean> accepted = subscribeConcurrently(
                drivers, DeliveryRequestSseRejectedException.Reason.DRIVER_LIMIT_EXCEEDED);

        assertThat(accepted.stream().filter(Boolean::booleanValue).count()).isEqualTo(2);
        assertThat(stream.subscriberCount()).isEqualTo(2);
        assertThat(stream.subscriberCount(FIRST_DRIVER)).isEqualTo(2);
    }

    @Test
    void concurrentSubscriptionsOfDifferentDriversNeverExceedTheTotalLimit() throws Exception {
        createStream();
        final List<DriverId> drivers = IntStream.rangeClosed(1, 20)
                .mapToObj(id -> new DriverId((long) id)).toList();

        final List<Boolean> accepted = subscribeConcurrently(
                drivers, DeliveryRequestSseRejectedException.Reason.TOTAL_LIMIT_EXCEEDED);

        assertThat(accepted.stream().filter(Boolean::booleanValue).count()).isEqualTo(MAX_TOTAL_CONNECTIONS);
        assertThat(stream.subscriberCount()).isEqualTo(MAX_TOTAL_CONNECTIONS);
        assertThat(drivers).allSatisfy(driver -> assertThat(stream.subscriberCount(driver)).isLessThanOrEqualTo(1));
    }

    @Test
    void blockedSendDoesNotBlockRegistrationOrCleanup() throws Exception {
        final BlockingSendSseEmitter blocked = new BlockingSendSseEmitter();
        final AtomicInteger created = new AtomicInteger();
        stream = new DeliveryRequestEventStream(MAX_TOTAL_CONNECTIONS,
                () -> created.getAndIncrement() == 0 ? blocked : new CapturingSseEmitter(), meterRegistry);
        final ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            final Future<SseEmitter> sending = executor.submit(() -> stream.subscribe(FIRST_DRIVER));
            assertThat(blocked.entered.await(5, TimeUnit.SECONDS)).isTrue();

            executor.submit(() -> stream.subscribe(SECOND_DRIVER)).get(5, TimeUnit.SECONDS);
            executor.submit(blocked::fireCompletion).get(5, TimeUnit.SECONDS);

            assertThat(stream.subscriberCount()).isOne();
            assertThat(stream.subscriberCount(FIRST_DRIVER)).isZero();
            assertThat(stream.subscriberCount(SECOND_DRIVER)).isOne();
            assertThat(sending.isDone()).isFalse();
            blocked.release.countDown();
            sending.get(5, TimeUnit.SECONDS);
        } finally {
            blocked.release.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void blockedCompletionDoesNotHoldTheLockAndNewSubscriptionsAreRejectedAfterClose() throws Exception {
        final BlockingCompleteSseEmitter blocked = new BlockingCompleteSseEmitter();
        final AtomicInteger created = new AtomicInteger();
        stream = new DeliveryRequestEventStream(MAX_TOTAL_CONNECTIONS,
                () -> created.getAndIncrement() == 0 ? blocked : new CapturingSseEmitter(), meterRegistry);
        stream.subscribe(FIRST_DRIVER);
        stream.subscribe(SECOND_DRIVER);
        final ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            final Future<?> closing = executor.submit(stream::close);
            assertThat(blocked.entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(activeSubscriptions()).isZero();
            assertThat(removalCount(DeliveryRequestEventStream.RemovalReason.SHUTDOWN)).isEqualTo(2);

            executor.submit(() -> assertRejected(SECOND_DRIVER,
                    DeliveryRequestSseRejectedException.Reason.STREAM_CLOSED)).get(5, TimeUnit.SECONDS);
            assertThat(executor.submit(() -> stream.subscriberCount()).get(5, TimeUnit.SECONDS)).isZero();
            assertThat(closing.isDone()).isFalse();
            blocked.release.countDown();
            closing.get(5, TimeUnit.SECONDS);
            assertThat(totalRemovals()).isEqualTo(2);
            assertThat(removalCount(DeliveryRequestEventStream.RemovalReason.COMPLETION)).isZero();
        } finally {
            blocked.release.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void closeDuringEmitterCreationPreventsLateRegistration() throws Exception {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        stream = new DeliveryRequestEventStream(MAX_TOTAL_CONNECTIONS, () -> {
            entered.countDown();
            await(release);
            return new CapturingSseEmitter();
        }, meterRegistry);
        final ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            final Future<?> subscribing = executor.submit(() ->
                    assertRejected(FIRST_DRIVER, DeliveryRequestSseRejectedException.Reason.STREAM_CLOSED));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

            executor.submit(stream::close).get(5, TimeUnit.SECONDS);
            release.countDown();
            subscribing.get(5, TimeUnit.SECONDS);

            assertThat(stream.subscriberCount()).isZero();
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void nonPositiveTotalLimitIsRejected(final int maxTotalConnections) {
        assertThatThrownBy(() -> new DeliveryRequestEventStream(
                maxTotalConnections, CapturingSseEmitter::new, meterRegistry))
                .isInstanceOf(IllegalArgumentException.class);
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
        stream = new DeliveryRequestEventStream(MAX_TOTAL_CONNECTIONS, FailingSseEmitter::new, meterRegistry);

        stream.subscribe(FIRST_DRIVER);

        assertThat(stream.subscriberCount()).isZero();
        assertThat(stream.subscriberCount(FIRST_DRIVER)).isZero();
        assertThat(activeSubscriptions()).isZero();
        assertThat(removalCount(DeliveryRequestEventStream.RemovalReason.SEND_FAILURE)).isOne();
    }

    @Test
    void notificationFailureDoesNotEscapeListener() {
        final CapturingSseEmitter emitter = new CapturingSseEmitter();
        stream = new DeliveryRequestEventStream(MAX_TOTAL_CONNECTIONS, () -> emitter, meterRegistry);
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

    @Test
    void metricsStartAtZeroAndRejectedConnectionsDoNotCreateRemovalEvents() {
        final List<CapturingSseEmitter> emitters = createStream();
        assertThat(activeSubscriptions()).isZero();
        assertThat(totalRemovals()).isZero();
        assertThat(meterRegistry.getMeters()).hasSize(6);

        stream.subscribe(FIRST_DRIVER);
        stream.subscribe(FIRST_DRIVER);
        assertRejected(FIRST_DRIVER, DeliveryRequestSseRejectedException.Reason.DRIVER_LIMIT_EXCEEDED);
        emitters.get(2).fireError();
        emitters.get(2).fireTimeout();
        emitters.get(2).fireCompletion();

        assertThat(activeSubscriptions()).isEqualTo(2);
        assertThat(totalRemovals()).isZero();
        assertThat(meterRegistry.getMeters()).allSatisfy(meter ->
                assertThat(meter.getId().getTags()).allSatisfy(tag ->
                        assertThat(tag.getKey()).isEqualTo("reason")));
    }

    @Test
    void concurrentCallbacksAndShutdownRecordEachSubscriptionOnce() throws Exception {
        final List<CapturingSseEmitter> emitters = createStream();
        stream.subscribe(FIRST_DRIVER);
        stream.subscribe(SECOND_DRIVER);
        final CapturingSseEmitter emitter = emitters.getFirst();
        final List<Runnable> signals = List.of(
                emitter::fireCompletion, emitter::fireTimeout, emitter::fireError, stream::close);
        final CountDownLatch ready = new CountDownLatch(signals.size());
        final CountDownLatch start = new CountDownLatch(1);
        final ExecutorService executor = Executors.newFixedThreadPool(signals.size());
        try {
            final List<Future<?>> attempts = signals.stream().<Future<?>>map(signal -> executor.submit(() -> {
                ready.countDown();
                await(start);
                signal.run();
            })).toList();
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (final Future<?> attempt : attempts) {
                attempt.get(5, TimeUnit.SECONDS);
            }
            emitter.fireCompletion();
            emitter.fireTimeout();
            emitter.fireError();
            stream.close();

            assertThat(activeSubscriptions()).isZero();
            assertThat(totalRemovals()).isEqualTo(2);
            assertThat(removalCount(DeliveryRequestEventStream.RemovalReason.SHUTDOWN)).isGreaterThanOrEqualTo(1);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void firstRemovalLogsItsTimeAndOriginalRequestIdAcrossThreads() throws Exception {
        final Logger logger = (Logger) LoggerFactory.getLogger(DeliveryRequestEventStream.class);
        final ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
        final String previousRequestId = MDC.get("requestId");
        try {
            final List<CapturingSseEmitter> emitters = createStream();
            MDC.put("requestId", "subscription-request");
            stream.subscribe(FIRST_DRIVER);
            MDC.remove("requestId");
            clock.add(Duration.ofSeconds(2));
            final long removedAt = clock.wallTime();

            try (final ExecutorService executor = Executors.newSingleThreadExecutor()) {
                executor.submit(emitters.getFirst()::fireError).get(5, TimeUnit.SECONDS);
            }
            clock.add(Duration.ofSeconds(5));
            emitters.getFirst().fireTimeout();
            emitters.getFirst().fireCompletion();

            assertThat(logs.list).hasSize(1);
            final ILoggingEvent event = logs.list.getFirst();
            final Map<String, Object> fields = new LinkedHashMap<>();
            event.getKeyValuePairs().forEach(pair -> fields.put(pair.key, pair.value));
            assertThat(fields).containsEntry("event", "delivery_request_sse_subscription_removed")
                    .containsEntry("request_id", "subscription-request")
                    .containsEntry("reason", "error")
                    .containsEntry("removed_at_epoch_ms", removedAt)
                    .containsEntry("subscribers_remaining", 0)
                    .containsEntry("exception_type", IOException.class.getName())
                    .containsKey("subscription_id")
                    .doesNotContainKeys("driver_id", "token");
            assertThat(event.getFormattedMessage()).contains("request_id=subscription-request", "reason=error",
                    "removed_at_epoch_ms=" + removedAt);
            assertThat(event.getFormattedMessage() + fields).doesNotContain("연결 오류");
            assertThat(event.getThrowableProxy()).isNull();
            assertThat(removalCount(DeliveryRequestEventStream.RemovalReason.ERROR)).isOne();
            assertThat(totalRemovals()).isOne();
        } finally {
            logger.detachAppender(logs);
            logs.stop();
            if (previousRequestId == null) {
                MDC.remove("requestId");
            } else {
                MDC.put("requestId", previousRequestId);
            }
        }
    }

    private double activeSubscriptions() {
        return meterRegistry.get(DeliveryRequestEventStream.ACTIVE_SUBSCRIPTIONS_METRIC).gauge().value();
    }

    private double removalCount(final DeliveryRequestEventStream.RemovalReason reason) {
        return meterRegistry.get(DeliveryRequestEventStream.REMOVED_SUBSCRIPTIONS_METRIC)
                .tag("reason", reason.tagValue()).counter().count();
    }

    private double totalRemovals() {
        return java.util.Arrays.stream(DeliveryRequestEventStream.RemovalReason.values())
                .mapToDouble(this::removalCount).sum();
    }

    private List<CapturingSseEmitter> createStream() {
        final List<CapturingSseEmitter> emitters = new CopyOnWriteArrayList<>();
        stream = new DeliveryRequestEventStream(MAX_TOTAL_CONNECTIONS, () -> {
            final CapturingSseEmitter emitter = new CapturingSseEmitter();
            emitters.add(emitter);
            return emitter;
        }, meterRegistry);
        return emitters;
    }

    private void assertRejected(final DriverId driverId, final DeliveryRequestSseRejectedException.Reason reason) {
        assertThatThrownBy(() -> stream.subscribe(driverId))
                .isInstanceOfSatisfying(DeliveryRequestSseRejectedException.class,
                        exception -> assertThat(exception.reason()).isEqualTo(reason));
    }

    private List<Boolean> subscribeConcurrently(
            final List<DriverId> drivers,
            final DeliveryRequestSseRejectedException.Reason expectedReason
    ) throws Exception {
        final CountDownLatch ready = new CountDownLatch(drivers.size());
        final CountDownLatch start = new CountDownLatch(1);
        final ExecutorService executor = Executors.newFixedThreadPool(drivers.size());
        try {
            final List<Future<Boolean>> attempts = drivers.stream().map(driver -> executor.submit(() -> {
                ready.countDown();
                await(start);
                try {
                    stream.subscribe(driver);
                    return true;
                } catch (final DeliveryRequestSseRejectedException exception) {
                    assertThat(exception.reason()).isEqualTo(expectedReason);
                    return false;
                }
            })).toList();
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            final List<Boolean> accepted = new CopyOnWriteArrayList<>();
            for (final Future<Boolean> attempt : attempts) {
                accepted.add(attempt.get(5, TimeUnit.SECONDS));
            }
            return accepted;
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static void await(final CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("동시성 검증의 대기 시간이 초과됐습니다.");
            }
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private enum Cleanup {
        COMPLETION, TIMEOUT, ERROR, SEND_FAILURE
    }

    private static class CapturingSseEmitter extends SseEmitter {

        private final List<String> events = new CopyOnWriteArrayList<>();
        private volatile RuntimeException failure;
        private Runnable completionCallback;
        private Runnable timeoutCallback;
        private Consumer<Throwable> errorCallback;
        private volatile boolean completed;

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

    private static final class BlockingSendSseEmitter extends CapturingSseEmitter {

        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        @Override
        public void send(final SseEventBuilder event) {
            entered.countDown();
            await(release);
            super.send(event);
        }
    }

    private static final class BlockingCompleteSseEmitter extends CapturingSseEmitter {

        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        @Override
        public void complete() {
            entered.countDown();
            await(release);
            super.complete();
            fireCompletion();
        }
    }

    private static final class FailingSseEmitter extends SseEmitter {

        @Override
        public void send(final SseEventBuilder event) throws IOException {
            throw new IOException("connection closed");
        }
    }
}
