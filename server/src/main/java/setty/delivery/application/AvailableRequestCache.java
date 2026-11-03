package setty.delivery.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import setty.delivery.application.readmodel.AvailableRequests;

/**
 * 배송 대기 목록의 전체 배열과 첫 페이지를 서버 메모리에 잠깐 둔다.
 * 목록이 바뀌면 배송원들이 한꺼번에 다시 조회하므로, 같은 결과를 DB에서 반복해 읽지 않게 한다.
 * 커서가 있는 다음 페이지와 상세는 요청마다 달라 두지 않는다.
 */
@Component
public class AvailableRequestCache {

    private final boolean enabled;
    private final Cache<Key, AvailableRequests> cache;

    public AvailableRequestCache(
            @Value("${setty.delivery.request-cache.enabled}") final boolean enabled,
            @Value("${setty.delivery.request-cache.ttl}") final Duration ttl,
            @Value("${setty.delivery.request-cache.maximum-size}") final long maximumSize
    ) {
        this.enabled = enabled;
        this.cache = Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfterWrite(ttl)
                .build();
    }

    public AvailableRequests getAll(final Supplier<AvailableRequests> loader) {
        return get(new Key(Kind.ALL, 0), loader);
    }

    public AvailableRequests getFirstPage(final int size, final Supplier<AvailableRequests> loader) {
        return get(new Key(Kind.FIRST_PAGE, size), loader);
    }

    public void invalidateAll() {
        cache.invalidateAll();
    }

    private AvailableRequests get(final Key key, final Supplier<AvailableRequests> loader) {
        if (!enabled) {
            return loader.get();
        }
        return cache.get(key, ignored -> loader.get());
    }

    private enum Kind {
        ALL, FIRST_PAGE
    }

    private record Key(Kind kind, int size) {
    }
}
