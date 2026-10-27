package setty.notification.listing.application;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
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

    @Async(NotificationSendConfiguration.DISPATCH_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onListingCreated(final ListingCreated event) {
        final ListingToNotify listing = ListingToNotify.from(event);
        listingNotificationService.createForListing(listing);
        dispatch(listing);
    }

    public void dispatch(final ListingToNotify listing) {
        final long started = System.nanoTime();
        final List<CompletableFuture<Long>> batches = sendAllPending(listing);
        final long retryable = batches.stream().mapToLong(CompletableFuture::join).sum();
        log.info("listing notification dispatched listingId={} batches={} retryable={} elapsedMs={}",
                listing.listingId(), batches.size(), retryable, (System.nanoTime() - started) / 1_000_000);
        if (retryable > 0) {
            throw new IllegalStateException(
                    "listing " + listing.listingId() + " has " + retryable + " notifications left to retry");
        }
    }

    private List<CompletableFuture<Long>> sendAllPending(final ListingToNotify listing) {
        final Semaphore inFlight = new Semaphore(properties.sendConcurrency() * IN_FLIGHT_BATCHES_PER_THREAD);
        final List<CompletableFuture<Long>> futures = new ArrayList<>();
        long afterId = 0L;
        while (true) {
            inFlight.acquireUninterruptibly();
            final List<ListingNotification> batch = listingNotificationService.findPendingBatch(listing.listingId(), afterId);
            if (batch.isEmpty()) {
                inFlight.release();
                break;
            }
            afterId = batch.getLast().getId();
            futures.add(CompletableFuture
                    .supplyAsync(() -> batchSender.send(batch, listing), sendExecutor)
                    .whenComplete((ignored, error) -> inFlight.release()));
        }
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        return futures;
    }
}
