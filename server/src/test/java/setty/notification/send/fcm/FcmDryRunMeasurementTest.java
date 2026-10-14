package setty.notification.send.fcm;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import setty.notification.send.NotificationSendProperties;
import setty.notification.send.NotificationSender;

@EnabledIfEnvironmentVariable(named = "FCM_DRY_RUN_TOKEN", matches = ".+")
@EnabledIfEnvironmentVariable(named = "SETTY_NOTIFICATION_FCM_CREDENTIALS_PATH", matches = ".+")
class FcmDryRunMeasurementTest {

    private static final int BATCH_SIZE = NotificationSender.MAXIMUM_BATCH_SIZE;
    private static final int WARMUP_ROUNDS = 2;
    private static final int MEASURED_ROUNDS = 10;

    @Test
    void 알림_500건을_dry_run으로_한_번에_보내는_시간을_잰다() throws FirebaseMessagingException, IOException {
        final FirebaseMessaging messaging = new FcmConfiguration().firebaseMessaging(
                new NotificationSendProperties(0, null, System.getenv("SETTY_NOTIFICATION_FCM_CREDENTIALS_PATH")));
        final List<Message> batch = batch(System.getenv("FCM_DRY_RUN_TOKEN"));

        for (int round = 0; round < WARMUP_ROUNDS; round++) {
            messaging.sendEach(batch, true);
        }
        final List<Long> elapsedMillis = new ArrayList<>();
        for (int round = 0; round < MEASURED_ROUNDS; round++) {
            final long started = System.nanoTime();
            final BatchResponse response = messaging.sendEach(batch, true);
            elapsedMillis.add(Duration.ofNanos(System.nanoTime() - started).toMillis());
            assertThat(response.getSuccessCount()).isEqualTo(BATCH_SIZE);
        }

        report(elapsedMillis);
    }

    private static List<Message> batch(final String token) {
        return IntStream.rangeClosed(1, BATCH_SIZE)
                .mapToObj(number -> Message.builder()
                        .setToken(token)
                        .putData("notificationId", String.valueOf(number))
                        .putData("listingId", "0")
                        .putData("title", "fcm dry run")
                        .setAndroidConfig(AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH).build())
                        .build())
                .toList();
    }

    private static void report(final List<Long> elapsedMillis) throws IOException {
        final StringBuilder csv = new StringBuilder("round,batch_size,elapsed_ms\n");
        for (int round = 0; round < elapsedMillis.size(); round++) {
            csv.append(round + 1).append(',').append(BATCH_SIZE).append(',').append(elapsedMillis.get(round)).append('\n');
        }
        final Path directory = Path.of("build", "reports", "fcm-dry-run");
        Files.createDirectories(directory);
        final Path file = directory.resolve("fcm-dry-run-%d.csv".formatted(System.currentTimeMillis()));
        Files.writeString(file, csv.toString());

        final List<Long> sorted = new ArrayList<>(elapsedMillis);
        Collections.sort(sorted);
        System.out.printf("FCM dry run %d건 × %d회: p50=%dms, p95=%dms, max=%dms (%s)%n",
                BATCH_SIZE, sorted.size(), percentile(sorted, 50), percentile(sorted, 95),
                sorted.getLast(), file.toAbsolutePath());
    }

    private static long percentile(final List<Long> sorted, final int percent) {
        final int rank = (int) Math.ceil(percent / 100.0 * sorted.size());
        return sorted.get(Math.max(rank, 1) - 1);
    }
}
