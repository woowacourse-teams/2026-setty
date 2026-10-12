package setty.delivery.api;

import static setty.delivery.api.DeliveryRequestSseRejectedException.Reason.DRIVER_LIMIT_EXCEEDED;
import static setty.delivery.api.DeliveryRequestSseRejectedException.Reason.STREAM_CLOSED;
import static setty.delivery.api.DeliveryRequestSseRejectedException.Reason.TOTAL_LIMIT_EXCEEDED;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import setty.delivery.application.DeliveryRequestsChanged;
import setty.delivery.domain.DriverId;

/**
 * 단일 서버에서 활성 SSE 연결을 관리한다. 이벤트에는 목록 변경 여부만 담는다.
 */
@Component
public class DeliveryRequestEventStream {

    private static final System.Logger LOGGER = System.getLogger(DeliveryRequestEventStream.class.getName());

    static final long CONNECTION_TIMEOUT_MILLIS = 120_000L;
    private static final long HEARTBEAT_INTERVAL_SECONDS = 15L;
    static final String CONNECTED_EVENT = "connected";
    static final String REQUESTS_CHANGED_EVENT = "delivery-requests-changed";
    private static final int MAX_CONNECTIONS_PER_DRIVER = 2;

    private final Map<UUID, Subscription> subscriptions = new ConcurrentHashMap<>();
    private final Object subscriptionsLock = new Object();
    private final int maxTotalConnections;
    private final Supplier<SseEmitter> emitterFactory;
    // 등록과 종료 상태 변경은 subscriptionsLock으로 함께 보호한다.
    private boolean closed;
    private final ScheduledExecutorService heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("delivery-request-sse-heartbeat").daemon(true).factory()
    );

    @Autowired
    public DeliveryRequestEventStream(
            @Value("${setty.delivery.request-sse.max-connections}") final int maxTotalConnections
    ) {
        this(maxTotalConnections, () -> new SseEmitter(CONNECTION_TIMEOUT_MILLIS));
    }

    DeliveryRequestEventStream(final int maxTotalConnections, final Supplier<SseEmitter> emitterFactory) {
        if (maxTotalConnections <= 0) {
            throw new IllegalArgumentException("배송 요청 SSE 전체 연결 상한은 1 이상이어야 합니다.");
        }
        this.maxTotalConnections = maxTotalConnections;
        this.emitterFactory = Objects.requireNonNull(emitterFactory);
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
        final Subscription subscription = new Subscription(driverId, emitter);

        emitter.onCompletion(() -> remove(subscriptionId, subscription));
        emitter.onTimeout(() -> remove(subscriptionId, subscription));
        emitter.onError(ignored -> remove(subscriptionId, subscription));
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
            LOGGER.log(System.Logger.Level.WARNING, "배송 요청 SSE 알림 전송에 실패했습니다.", exception);
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
        final List<Subscription> closingSubscriptions;
        synchronized (subscriptionsLock) {
            if (closed) {
                return;
            }
            closed = true;
            closingSubscriptions = List.copyOf(subscriptions.values());
            subscriptions.clear();
        }
        heartbeatScheduler.shutdownNow();
        closingSubscriptions.forEach(subscription -> subscription.emitter().complete());
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
        } catch (final IOException | IllegalStateException ignored) {
            remove(subscriptionId, subscription);
        }
    }

    private void remove(final UUID subscriptionId, final Subscription subscription) {
        synchronized (subscriptionsLock) {
            subscriptions.remove(subscriptionId, subscription);
        }
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

    private record Subscription(DriverId driverId, SseEmitter emitter) {
    }
}
