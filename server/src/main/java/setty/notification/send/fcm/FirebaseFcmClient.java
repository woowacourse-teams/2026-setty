package setty.notification.send.fcm;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.SendResponse;
import java.util.List;
import java.util.Set;

public class FirebaseFcmClient implements FcmClient {

    private static final Set<MessagingErrorCode> INVALID_TOKEN_CODES = Set.of(
            MessagingErrorCode.UNREGISTERED,
            MessagingErrorCode.INVALID_ARGUMENT,
            MessagingErrorCode.SENDER_ID_MISMATCH
    );

    private final FirebaseMessaging firebaseMessaging;

    public FirebaseFcmClient(final FirebaseMessaging firebaseMessaging) {
        this.firebaseMessaging = firebaseMessaging;
    }

    @Override
    public List<FcmDelivery> sendEach(final List<Message> messages) {
        try {
            final BatchResponse response = firebaseMessaging.sendEach(messages);
            return response.getResponses().stream().map(this::toDelivery).toList();
        } catch (final FirebaseMessagingException sendFailed) {
            return messages.stream().map(message -> FcmDelivery.failure()).toList();
        }
    }

    private FcmDelivery toDelivery(final SendResponse response) {
        if (response.isSuccessful()) {
            return FcmDelivery.success();
        }
        final FirebaseMessagingException exception = response.getException();
        final MessagingErrorCode code = exception == null ? null : exception.getMessagingErrorCode();
        if (code != null && INVALID_TOKEN_CODES.contains(code)) {
            return FcmDelivery.invalidToken();
        }
        return FcmDelivery.failure();
    }
}
