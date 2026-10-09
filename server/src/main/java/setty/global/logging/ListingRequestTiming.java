package setty.global.logging;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 동기 목록 요청 한 건의 구간 기록. 필터가 연 범위에서만 수집하고 한 줄로 출력한다. */
public final class ListingRequestTiming implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ListingRequestTiming.class);
    private static final ThreadLocal<ListingRequestTiming> CURRENT = new ThreadLocal<>();

    private final ListingRequestTiming previous;
    private final long startedAt = System.nanoTime();
    private final Map<String, Object> fields = new LinkedHashMap<>();

    private ListingRequestTiming() {
        previous = CURRENT.get();
        CURRENT.set(this);
    }

    static ListingRequestTiming open() {
        return new ListingRequestTiming();
    }

    public static <T> T measure(String stage, Supplier<T> action) {
        ListingRequestTiming timing = CURRENT.get();
        if (timing == null) {
            return action.get();
        }
        long startedAt = System.nanoTime();
        boolean completed = false;
        try {
            T result = action.get();
            completed = true;
            return result;
        } finally {
            timing.fields.put(stage + "_ms", millisecondsSince(startedAt));
            timing.fields.put(stage + "_completed", completed);
        }
    }

    public static void count(String name, int value) {
        ListingRequestTiming timing = CURRENT.get();
        if (timing != null) {
            timing.fields.put(name, value);
        }
    }

    void log(String outcome, Integer status, boolean complete, Throwable failure) {
        // 출력 자체의 비용은 filter_ms에 포함하지 않는다. requestId는 바깥 필터의 MDC에서 가져온다.
        var event = log.atInfo()
                .addKeyValue("event", "listing_request_timing")
                .addKeyValue("method", "GET")
                .addKeyValue("path", "/api/listings")
                .addKeyValue("status", status)
                .addKeyValue("outcome", outcome)
                .addKeyValue("response_complete", complete)
                .addKeyValue("filter_ms", millisecondsSince(startedAt));
        fields.forEach(event::addKeyValue);
        if (failure != null) {
            event.addKeyValue("exception_type", failure.getClass().getSimpleName());
        }
        event.log("listing request timing");
    }

    private static double millisecondsSince(long start) {
        return (System.nanoTime() - start) / 1_000_000.0;
    }

    @Override
    public void close() {
        if (previous == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(previous);
        }
    }
}
