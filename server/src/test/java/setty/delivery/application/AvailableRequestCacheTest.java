package setty.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import setty.delivery.application.readmodel.AvailableRequests;

class AvailableRequestCacheTest {

    private final AvailableRequestCache cache = new AvailableRequestCache(true, Duration.ofSeconds(30), 16);
    private final AtomicInteger loads = new AtomicInteger();

    @Test
    void 두_번째_조회는_저장된_결과를_쓴다() {
        final AvailableRequests first = cache.getFirstPage(20, loader("first"));
        final AvailableRequests second = cache.getFirstPage(20, loader("second"));

        assertThat(second).isSameAs(first);
        assertThat(loads).hasValue(1);
    }

    @Test
    void 전체와_페이지_크기별_첫_페이지는_따로_저장한다() {
        cache.getAll(loader("all"));
        cache.getFirstPage(20, loader("twenty"));
        cache.getFirstPage(10, loader("ten"));

        assertThat(cache.getAll(loader("again")).nextCursor()).isEqualTo("all");
        assertThat(cache.getFirstPage(10, loader("again")).nextCursor()).isEqualTo("ten");
        assertThat(loads).hasValue(3);
    }

    @Test
    void 무효화하면_다음_조회에서_다시_읽는다() {
        cache.getAll(loader("before"));
        cache.getFirstPage(20, loader("before"));

        cache.invalidateAll();

        assertThat(cache.getAll(loader("after")).nextCursor()).isEqualTo("after");
        assertThat(cache.getFirstPage(20, loader("after")).nextCursor()).isEqualTo("after");
        assertThat(loads).hasValue(4);
    }

    @Test
    void 목록_변경_이벤트를_받으면_비운다() {
        cache.getFirstPage(20, loader("before"));

        cache.on(new DeliveryRequestsChanged());

        assertThat(cache.getFirstPage(20, loader("after")).nextCursor()).isEqualTo("after");
    }

    @Test
    void 조회_중에_무효화되면_그_결과는_해당_요청에만_돌려주고_다시_쓰지_않는다() throws Exception {
        final CountDownLatch loading = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final CompletableFuture<AvailableRequests> slow = CompletableFuture.supplyAsync(() ->
                cache.getFirstPage(20, () -> {
                    loading.countDown();
                    await(release);
                    return requests("stale");
                }));
        assertThat(loading.await(5, TimeUnit.SECONDS)).isTrue();

        cache.invalidateAll();
        release.countDown();

        assertThat(slow.get(5, TimeUnit.SECONDS).nextCursor()).isEqualTo("stale");
        assertThat(cache.getFirstPage(20, loader("fresh")).nextCursor()).isEqualTo("fresh");
        assertThat(cache.getFirstPage(20, loader("unused")).nextCursor()).isEqualTo("fresh");
    }

    @Test
    void 무효화_뒤에_시작한_조회는_진행_중인_이전_조회를_기다리지_않는다() throws Exception {
        final CountDownLatch loading = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final CompletableFuture<AvailableRequests> slow = CompletableFuture.supplyAsync(() ->
                cache.getAll(() -> {
                    loading.countDown();
                    await(release);
                    return requests("stale");
                }));
        assertThat(loading.await(5, TimeUnit.SECONDS)).isTrue();

        cache.invalidateAll();

        assertThat(cache.getAll(loader("fresh")).nextCursor()).isEqualTo("fresh");
        release.countDown();
        assertThat(slow.get(5, TimeUnit.SECONDS).nextCursor()).isEqualTo("stale");
        assertThat(cache.getAll(loader("unused")).nextCursor()).isEqualTo("fresh");
    }

    @Test
    void 동시에_비어_있는_항목을_조회하면_한_번만_읽는다() throws Exception {
        final CountDownLatch start = new CountDownLatch(1);
        final List<Future<AvailableRequests>> results = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
            for (int i = 0; i < 8; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    return cache.getFirstPage(20, () -> {
                        sleep();
                        return loader("shared").get();
                    });
                }));
            }
            start.countDown();
            for (Future<AvailableRequests> result : results) {
                assertThat(result.get(5, TimeUnit.SECONDS).nextCursor()).isEqualTo("shared");
            }
        }

        assertThat(loads).hasValue(1);
    }

    @Test
    void 비활성화하면_항상_새로_읽는다() {
        final AvailableRequestCache disabled = new AvailableRequestCache(false, Duration.ofSeconds(30), 16);

        disabled.getFirstPage(20, loader("first"));
        disabled.getAll(loader("second"));
        disabled.getAll(loader("third"));

        assertThat(loads).hasValue(3);
    }

    @Test
    void 결과는_요청_사이에_공유되므로_바꿀_수_없다() {
        final AvailableRequests requests = cache.getAll(loader("all"));

        assertThat(requests.items()).isUnmodifiable();
        assertThat(requests.thumbnailUrls()).isUnmodifiable();
    }

    private Supplier<AvailableRequests> loader(final String marker) {
        return () -> {
            loads.incrementAndGet();
            return requests(marker);
        };
    }

    private static AvailableRequests requests(final String marker) {
        return new AvailableRequests(new ArrayList<>(), new java.util.HashMap<>(Map.of(1L, "https://images/1.jpg")),
                marker);
    }

    private static void await(final CountDownLatch latch) {
        try {
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private static void sleep() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
