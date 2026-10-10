package setty.notification.listing.application;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import setty.common.ListingCreated;
import setty.notification.listing.domain.ListingNotification;
import setty.notification.send.NotificationSendConfiguration;
import setty.notification.send.NotificationSendProperties;

@Component
public class ListingNotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ListingNotificationDispatcher.class);
    private static final int IN_FLIGHT_BATCHES_PER_THREAD = 2;

    private final ListingNotificationService listingNotificationService;
    private final NotificationBatchSender batchSender;
    private final Executor sendExecutor;
    private final NotificationSendProperties properties;

    public ListingNotificationDispatcher(
            final ListingNotificationService listingNotificationService,
            final NotificationBatchSender batchSender,
            @Qualifier(NotificationSendConfiguration.SEND_EXECUTOR) final Executor sendExecutor,
            final NotificationSendProperties properties
    ) {
        this.listingNotificationService = listingNotificationService;
        this.batchSender = batchSender;
        this.sendExecutor = sendExecutor;
        this.properties = properties;
    }

    @ApplicationModuleListener
    public void onListingCreated(final ListingCreated event) {
        dispatch(event.listingId(), event.title());
    }

    public void dispatch(final Long listingId, final String title) {
        final long started = System.nanoTime();
        final int batches = sendAllPending(listingId, title);
        final long retryable = listingNotificationService.countPending(listingId);
        log.info("listing notification dispatched listingId={} batches={} retryable={} elapsedMs={}",
                listingId, batches, retryable, (System.nanoTime() - started) / 1_000_000);
        if (retryable > 0) {
            throw new IllegalStateException(
                    "listing " + listingId + " has " + retryable + " notifications left to retry");
        }
    }

    private int sendAllPending(final Long listingId, final String title) {
        final Semaphore inFlight = new Semaphore(properties.sendConcurrency() * IN_FLIGHT_BATCHES_PER_THREAD);
        final List<CompletableFuture<Void>> futures = new ArrayList<>();
        long afterId = 0L;
        while (true) {
            inFlight.acquireUninterruptibly();
            final List<ListingNotification> batch = listingNotificationService.findPendingBatch(listingId, afterId);
            if (batch.isEmpty()) {
                inFlight.release();
                break;
            }
            afterId = batch.getLast().getId();
            futures.add(CompletableFuture
                    .runAsync(() -> batchSender.send(batch, title), sendExecutor)
                    .whenComplete((ignored, error) -> inFlight.release()));
        }
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        return futures.size();
    }
}
