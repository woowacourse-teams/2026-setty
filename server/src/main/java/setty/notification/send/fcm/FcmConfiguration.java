package setty.notification.send.fcm;

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import setty.notification.send.NotificationSendProperties;

@Configuration
@ConditionalOnProperty(name = "setty.notification.sender", havingValue = "fcm")
public class FcmConfiguration {

    private static final int TIMEOUT_MILLIS = (int) Duration.ofSeconds(10).toMillis();

    @Bean
    public FirebaseMessaging firebaseMessaging(final NotificationSendProperties properties) {
        try (FileInputStream credentials = new FileInputStream(properties.fcmCredentialsPath())) {
            final FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(credentials))
                    .setHttpTransport(new NetHttpTransport())
                    .setConnectTimeout(TIMEOUT_MILLIS)
                    .setReadTimeout(TIMEOUT_MILLIS)
                    .build();
            final FirebaseApp app = FirebaseApp.getApps().isEmpty()
                    ? FirebaseApp.initializeApp(options)
                    : FirebaseApp.getInstance();
            return FirebaseMessaging.getInstance(app);
        } catch (final IOException unreadable) {
            throw new UncheckedIOException("FCM credentials unreadable: " + properties.fcmCredentialsPath(), unreadable);
        }
    }

    @Bean
    public FcmClient fcmClient(final FirebaseMessaging firebaseMessaging) {
        return new FirebaseFcmClient(firebaseMessaging);
    }
}
