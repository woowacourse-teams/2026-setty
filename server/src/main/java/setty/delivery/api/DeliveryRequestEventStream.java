package setty.delivery.api;

import static setty.delivery.api.DeliveryRequestSseRejectedException.Reason.DRIVER_LIMIT_EXCEEDED;
import static setty.delivery.api.DeliveryRequestSseRejectedException.Reason.STREAM_CLOSED;
import static setty.delivery.api.DeliveryRequestSseRejectedException.Reason.TOTAL_LIMIT_EXCEEDED;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import setty.delivery.application.DeliveryRequestsChanged;
import setty.delivery.domain.DriverId;
import setty.global.logging.SseCompletionLogging;

/**
 * 단일 서버에서 활성 SSE 연결을 관리한다. 이벤트에는 목록 변경 여부만 담는다.
 */
@Component
public class DeliveryRequestEventStream {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeliveryRequestEventStream.class);

    static final String ACTIVE_SUBSCRIPTIONS_METRIC = "setty.delivery.request.sse.subscriptions.active";
    static final String REMOVED_SUBSCRIPTIONS_METRIC = "setty.delivery.request.sse.subscriptions.removed";
    static final long CONNECTION_TIMEOUT_MILLIS = 120_000L;
    private static final long HEARTBEAT_INTERVAL_SECONDS = 15L;
    static final String CONNECTED_EVENT = "connected";
    static final String REQUESTS_CHANGED_EVENT = "delivery-requests-changed";
    private static final int MAX_CONNECTIONS_PER_DRIVER = 2;

    private final Map<UUID, Subscription> subscriptions = new ConcurrentHashMap<>();
    private final Object subscriptionsLock = new Object();
    private final int maxTotalConnections;
    private final Supplier<SseEmitter> emitterFactory;
    private final Clock clock;
    private final Map<RemovalReason, Counter> removalCounters = new EnumMap<>(RemovalReason.class);
    // 등록과 종료 상태 변경은 subscriptionsLock으로 함께 보호한다.
    private boolean closed;
    private final ScheduledExecutorService heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("delivery-request-sse-heartbeat").daemon(true).factory()
    );

    @Autowired
    public DeliveryRequestEventStream(
            @Value("${setty.delivery.request-sse.max-connections}") final int maxTotalConnections,
            final MeterRegistry meterRegistry
    ) {
        this(maxTotalConnections, () -> new SseEmitter(CONNECTION_TIMEOUT_MILLIS), meterRegistry);
    }

    DeliveryRequestEventStream(
            final int maxTotalConnections,
            final Supplier<SseEmitter> emitterFactory,
            final MeterRegistry meterRegistry
    ) {
        if (maxTotalConnections <= 0) {
            throw new IllegalArgumentException("배송 요청 SSE 전체 연결 상한은 1 이상이어야 합니다.");
        }
        this.maxTotalConnections = maxTotalConnections;
        this.emitterFactory = Objects.requireNonNull(emitterFactory);
        this.clock = Objects.requireNonNull(meterRegistry).config().clock();
        Gauge.builder(ACTIVE_SUBSCRIPTIONS_METRIC, this, DeliveryRequestEventStream::subscriberCount)
                .description("Current registered delivery request SSE subscriptions")
                .register(meterRegistry);
        for (final RemovalReason reason : RemovalReason.values()) {
            removalCounters.put(reason, Counter.builder(REMOVED_SUBSCRIPTIONS_METRIC)
                    .description("First removals of registered delivery request SSE subscriptions")
                    .tag("reason", reason.tagValue())
                    .register(meterRegistry));
        }
    }

    @PostConstruct
    void startHeartbeat() {
        heartbeatScheduler.scheduleWithFixedDelay(
                this::sendHeartbeat,
                HEARTBEAT_INTERVAL_SECONDS,
                HEARTBEAT_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );
    }

    public SseEmitter subscribe(final DriverId driverId) {
        Objects.requireNonNull(driverId);
        final SseEmitter emitter = emitterFactory.get();
        final UUID subscriptionId = UUID.randomUUID();
        final Subscription subscription = new Subscription(driverId, emitter, MDC.get("requestId"));

        emitter.onCompletion(() -> {
            final long completedAtEpochMillis = clock.wallTime();
            final long completedAtNanos = clock.monotonicTime();
            remove(subscriptionId, subscription, RemovalReason.COMPLETION, null);
            SseCompletionLogging.completed(subscriptionId, subscription.requestId(),
                    completedAtEpochMillis, completedAtNanos);
        });
        emitter.onTimeout(() -> remove(subscriptionId, subscription, RemovalReason.TIMEOUT, null));
        emitter.onError(exception -> remove(subscriptionId, subscription, RemovalReason.ERROR, exception));
        register(subscriptionId, subscription);
        send(subscriptionId, subscription, SseEmitter.event().name(CONNECTED_EVENT).data("{}"));
        return emitter;
    }

    // 커밋 전에 알리면 기사 앱이 아직 반영되지 않은 목록을 다시 조회하므로 커밋 후에 보낸다.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(final DeliveryRequestsChanged event) {
        try {
            notifyRequestsChanged();
        } catch (final RuntimeException exception) {
            LOGGER.warn("배송 요청 SSE 알림 전송에 실패했습니다.", exception);
        }
    }

    void notifyRequestsChanged() {
        subscriptions.forEach((subscriptionId, subscription) -> send(
                subscriptionId,
                subscription,
                SseEmitter.event().name(REQUESTS_CHANGED_EVENT).data("{}")
        ));
    }

    @PreDestroy
    void close() {
        final List<Removal> removals;
        synchronized (subscriptionsLock) {
            if (closed) {
                return;
            }
            closed = true;
            final List<Map.Entry<UUID, Subscription>> closingSubscriptions = List.copyOf(subscriptions.entrySet());
            subscriptions.clear();
            final long removedAtEpochMillis = clock.wallTime();
            final long removedAtNanos = clock.monotonicTime();
            removals = closingSubscriptions.stream()
                    .map(entry -> new Removal(entry.getKey(), entry.getValue(), RemovalReason.SHUTDOWN,
                            removedAtEpochMillis, removedAtNanos, 0, null))
                    .toList();
        }
        heartbeatScheduler.shutdownNow();
        removals.forEach(this::recordRemoval);
        removals.forEach(removal -> removal.subscription().emitter().complete());
    }

    int subscriberCount() {
        synchronized (subscriptionsLock) {
            return subscriptions.size();
        }
    }

    int subscriberCount(final DriverId driverId) {
        synchronized (subscriptionsLock) {
            return countByDriver(driverId);
        }
    }

    private void sendHeartbeat() {
        subscriptions.forEach((subscriptionId, subscription) -> send(
                subscriptionId,
                subscription,
                SseEmitter.event().comment("heartbeat")
        ));
    }

    private void send(
            final UUID subscriptionId,
            final Subscription subscription,
            final SseEmitter.SseEventBuilder event
    ) {
        try {
            subscription.emitter().send(event);
        } catch (final IOException | IllegalStateException exception) {
            remove(subscriptionId, subscription, RemovalReason.SEND_FAILURE, exception);
        }
    }

    private void remove(
            final UUID subscriptionId,
            final Subscription subscription,
            final RemovalReason reason,
            final Throwable exception
    ) {
        final Removal removal;
        synchronized (subscriptionsLock) {
            if (!subscriptions.remove(subscriptionId, subscription)) {
                return;
            }
            removal = new Removal(subscriptionId, subscription, reason,
                    clock.wallTime(), clock.monotonicTime(), subscriptions.size(), exception);
        }
        recordRemoval(removal);
    }

    // 구독 맵의 최초 제거만 기록한다. HTTP 비동기 요청의 완료 여부와는 별개다.
    private void recordRemoval(final Removal removal) {
        removalCounters.get(removal.reason()).increment();
        final Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("event", "delivery_request_sse_subscription_removed");
        fields.put("subscription_id", removal.subscriptionId());
        fields.put("request_id", removal.subscription().requestId());
        fields.put("reason", removal.reason().tagValue());
        fields.put("removed_at_epoch_ms", removal.removedAtEpochMillis());
        fields.put("removed_at_nanos", removal.removedAtNanos());
        fields.put("subscribers_remaining", removal.subscribersRemaining());
        if (removal.exception() != null) {
            fields.put("exception_type", removal.exception().getClass().getName());
        }
        final var event = LOGGER.atInfo();
        fields.forEach(event::addKeyValue);
        // 기본 텍스트 로그에도 필드를 남기고 dev/prod JSON 로그에서는 개별 속성으로도 제공한다.
        event.log("delivery request SSE subscription removed {}", fields);
    }

    private void register(final UUID subscriptionId, final Subscription subscription) {
        synchronized (subscriptionsLock) {
            if (closed) {
                throw new DeliveryRequestSseRejectedException(STREAM_CLOSED);
            }
            if (subscriptions.size() >= maxTotalConnections) {
                throw new DeliveryRequestSseRejectedException(TOTAL_LIMIT_EXCEEDED);
            }
            if (countByDriver(subscription.driverId()) >= MAX_CONNECTIONS_PER_DRIVER) {
                throw new DeliveryRequestSseRejectedException(DRIVER_LIMIT_EXCEEDED);
            }
            subscriptions.put(subscriptionId, subscription);
        }
    }

    // subscriptionsLock을 획득한 상태에서만 호출한다.
    private int countByDriver(final DriverId driverId) {
        return (int) subscriptions.values().stream()
                .filter(subscription -> subscription.driverId().equals(driverId))
                .count();
    }

    enum RemovalReason {
        COMPLETION, TIMEOUT, ERROR, SEND_FAILURE, SHUTDOWN;

        String tagValue() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private record Subscription(DriverId driverId, SseEmitter emitter, String requestId) {
    }

    private record Removal(
            UUID subscriptionId,
            Subscription subscription,
            RemovalReason reason,
            long removedAtEpochMillis,
            long removedAtNanos,
            int subscribersRemaining,
            Throwable exception
    ) {
    }
}
