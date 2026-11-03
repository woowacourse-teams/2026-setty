package setty.delivery.application;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import setty.delivery.application.readmodel.AvailableRequests;

/**
 * 배송 대기 목록의 전체 배열과 첫 페이지를 서버 메모리에 잠깐 둔다.
 * 목록이 바뀌면 배송원들이 한꺼번에 다시 조회하므로, 같은 결과를 DB에서 반복해 읽지 않게 한다.
 * 커서가 있는 다음 페이지와 상세는 요청마다 달라 두지 않는다.
 *
 * <p>키에 무효화 세대를 넣는다. 변경 커밋 전에 시작한 조회가 무효화 뒤에 끝나도 그 결과는 이전 세대 키에만 남고,
 * 무효화 뒤의 요청은 새 세대 키로 다시 조회한다. 같은 키를 동시에 조회하면 먼저 온 요청 하나만 DB를 읽는다.
 */
@Component
public class AvailableRequestCache {

    private final boolean enabled;
    private final AsyncCache<Key, AvailableRequests> cache;
    private final AtomicLong generation = new AtomicLong();

    public AvailableRequestCache(
            @Value("${setty.delivery.request-cache.enabled}") final boolean enabled,
            @Value("${setty.delivery.request-cache.ttl}") final Duration ttl,
            @Value("${setty.delivery.request-cache.maximum-size}") final long maximumSize
    ) {
        this.enabled = enabled;
        this.cache = Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfterWrite(ttl)
                .buildAsync();
    }

    public AvailableRequests getAll(final Supplier<AvailableRequests> loader) {
        return get(Kind.ALL, 0, loader);
    }

    public AvailableRequests getFirstPage(final int size, final Supplier<AvailableRequests> loader) {
        return get(Kind.FIRST_PAGE, size, loader);
    }

    // SSE 알림보다 먼저 비운다. 알림을 받은 배송원이 다시 조회할 때 이전 목록을 받지 않게 한다.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public void on(final DeliveryRequestsChanged event) {
        invalidateAll();
    }

    public void invalidateAll() {
        generation.incrementAndGet();
        cache.synchronous().invalidateAll();
    }

    private AvailableRequests get(final Kind kind, final int size, final Supplier<AvailableRequests> loader) {
        if (!enabled) {
            return loader.get();
        }
        final Key key = new Key(generation.get(), kind, size);
        final CompletableFuture<AvailableRequests> created = new CompletableFuture<>();
        // 맵에는 빈 future만 넣고 조회는 요청 스레드에서 한다. 조회하는 동안 다른 키의 조회를 막지 않는다.
        final CompletableFuture<AvailableRequests> found = cache.get(key, (ignored, executor) -> created);
        if (found != created) {
            return join(found);
        }
        try {
            created.complete(loader.get());
        } catch (final RuntimeException | Error exception) {
            // 실패한 future는 캐시가 지운다. 기다리던 요청도 같은 예외를 받는다.
            created.completeExceptionally(exception);
            throw exception;
        }
        if (generation.get() != key.generation()) {
            cache.asMap().remove(key, created);
        }
        return created.join();
    }

    private static AvailableRequests join(final CompletableFuture<AvailableRequests> future) {
        try {
            return future.join();
        } catch (final CompletionException exception) {
            if (exception.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw exception;
        }
    }

    private enum Kind {
        ALL, FIRST_PAGE
    }

    private record Key(long generation, Kind kind, int size) {
    }
}
