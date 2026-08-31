package setty.delivery.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class DeliveryRequestsChangedListener {

    private static final System.Logger LOGGER = System.getLogger(DeliveryRequestsChangedListener.class.getName());

    private final DeliveryRequestNotifier deliveryRequestNotifier;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(final DeliveryRequestsChanged event) {
        try {
            deliveryRequestNotifier.notifyRequestsChanged();
        } catch (final RuntimeException exception) {
            LOGGER.log(System.Logger.Level.WARNING, "배송 요청 SSE 알림 전송에 실패했습니다.", exception);
        }
    }
}
