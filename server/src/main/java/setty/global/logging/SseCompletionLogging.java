package setty.global.logging;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.NamedInterface;

@NamedInterface("logging")
public final class SseCompletionLogging {

    private static final Logger LOGGER = LoggerFactory.getLogger(SseCompletionLogging.class);

    private SseCompletionLogging() {
    }

    public static void completed(
            final UUID subscriptionId,
            final String requestId,
            final long completedAtEpochMillis,
            final long completedAtNanos
    ) {
        LOGGER.atInfo()
                .addKeyValue("event", "delivery_request_sse_http_completed")
                .addKeyValue("subscription_id", subscriptionId)
                .addKeyValue("request_id", requestId)
                .addKeyValue("completed_at_epoch_ms", completedAtEpochMillis)
                .addKeyValue("completed_at_nanos", completedAtNanos)
                .log("delivery_request_sse_http_completed subscription_id={} request_id={} "
                                + "completed_at_epoch_ms={} completed_at_nanos={}",
                        subscriptionId, requestId, completedAtEpochMillis, completedAtNanos);
    }
}
