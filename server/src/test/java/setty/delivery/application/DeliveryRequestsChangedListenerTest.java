package setty.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

class DeliveryRequestsChangedListenerTest {

    @Test
    void notifiesRequestEventSubscribersAfterCommit() {
        final DeliveryRequestNotifier notifier = mock(DeliveryRequestNotifier.class);
        final DeliveryRequestsChangedListener listener = new DeliveryRequestsChangedListener(notifier);

        listener.handle(new DeliveryRequestsChanged());

        verify(notifier).notifyRequestsChanged();
    }

    @Test
    void notificationFailureDoesNotEscapeListener() {
        final DeliveryRequestNotifier notifier = mock(DeliveryRequestNotifier.class);
        doThrow(new IllegalStateException("연결 종료"))
                .when(notifier)
                .notifyRequestsChanged();
        final DeliveryRequestsChangedListener listener = new DeliveryRequestsChangedListener(notifier);

        assertThatCode(() -> listener.handle(new DeliveryRequestsChanged()))
                .doesNotThrowAnyException();
    }

    @Test
    void listenerRunsAfterCommit() throws NoSuchMethodException {
        final Method handle = DeliveryRequestsChangedListener.class
                .getMethod("handle", DeliveryRequestsChanged.class);

        final TransactionalEventListener annotation = handle.getAnnotation(TransactionalEventListener.class);

        assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }
}
