package setty.delivery.api;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import setty.delivery.application.DeliveryRequestNotifier;

/**
 * 단일 서버에서 활성 SSE 연결을 관리한다. 이벤트에는 목록 변경 여부만 담는다.
 */
@Component
public class DeliveryRequestEventStream implements DeliveryRequestNotifier {

    static final long CONNECTION_TIMEOUT_MILLIS = 120_000L;
    private static final long HEARTBEAT_INTERVAL_SECONDS = 15L;
    static final String CONNECTED_EVENT = "connected";
    static final String REQUESTS_CHANGED_EVENT = "delivery-requests-changed";

    private final Map<UUID, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final Supplier<SseEmitter> emitterFactory;
    private final ScheduledExecutorService heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("delivery-request-sse-heartbeat").daemon(true).factory()
    );

    public DeliveryRequestEventStream() {
        this(() -> new SseEmitter(CONNECTION_TIMEOUT_MILLIS));
    }

    DeliveryRequestEventStream(final Supplier<SseEmitter> emitterFactory) {
        this.emitterFactory = emitterFactory;
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

    public SseEmitter subscribe() {
        final SseEmitter emitter = emitterFactory.get();
        final UUID emitterId = UUID.randomUUID();

        emitters.put(emitterId, emitter);
        emitter.onCompletion(() -> remove(emitterId, emitter));
        emitter.onTimeout(() -> remove(emitterId, emitter));
        emitter.onError(ignored -> remove(emitterId, emitter));
        send(emitterId, emitter, SseEmitter.event().name(CONNECTED_EVENT).data("{}"));
        return emitter;
    }

    @Override
    public void notifyRequestsChanged() {
        emitters.forEach((emitterId, emitter) -> send(
                emitterId,
                emitter,
                SseEmitter.event().name(REQUESTS_CHANGED_EVENT).data("{}")
        ));
    }

    @PreDestroy
    void close() {
        heartbeatScheduler.shutdownNow();
        emitters.forEach((emitterId, emitter) -> emitter.complete());
        emitters.clear();
    }

    int subscriberCount() {
        return emitters.size();
    }

    private void sendHeartbeat() {
        emitters.forEach((emitterId, emitter) -> send(
                emitterId,
                emitter,
                SseEmitter.event().comment("heartbeat")
        ));
    }

    private void send(final UUID emitterId, final SseEmitter emitter, final SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (final IOException | IllegalStateException ignored) {
            remove(emitterId, emitter);
        }
    }

    private void remove(final UUID emitterId, final SseEmitter emitter) {
        emitters.remove(emitterId, emitter);
    }
}
