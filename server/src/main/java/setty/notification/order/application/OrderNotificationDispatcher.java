package setty.notification.order.application;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import setty.common.OrderStatusNotified;
import setty.notification.send.NotificationMessage;
import setty.notification.send.NotificationSender;
import setty.notification.send.SendResult;

// 받는 사람이 한두 명이라 배치 없이 바로 보낸다. 키워드 알림 대량 발송 뒤에 밀리지 않게 주문·배송 리스너와 같은 공용 실행기를 쓴다.
// 행 저장과 발송을 한 트랜잭션에 묶지 않아 발송 실패가 저장을 되돌리지 않는다.
// 재시도할 알림이 남으면 예외로 발행 기록을 남겨 재발행 스케줄러가 다시 부르게 한다.
@Component
public class OrderNotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OrderNotificationDispatcher.class);

    private final OrderNotificationService orderNotificationService;
    private final NotificationSender notificationSender;

    public OrderNotificationDispatcher(
            final OrderNotificationService orderNotificationService,
            final NotificationSender notificationSender
    ) {
        this.orderNotificationService = orderNotificationService;
        this.notificationSender = notificationSender;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderStatusNotified(final OrderStatusNotified event) {
        dispatch(event);
    }

    public void dispatch(final OrderStatusNotified event) {
        final List<PendingOrderNotification> pending = orderNotificationService.createFor(event);
        if (pending.isEmpty()) {
            return;
        }
        final List<NotificationMessage> messages = pending.stream().map(notification -> toMessage(notification, event)).toList();
        final SendResult result = sendSafely(messages);
        final List<Long> sent = new ArrayList<>();
        final List<Long> failed = new ArrayList<>();
        for (final PendingOrderNotification notification : pending) {
            (result.failed(notification.id()) ? failed : sent).add(notification.id());
        }
        orderNotificationService.markSent(sent);
        final long retryable = orderNotificationService.markFailedAttempt(failed);
        if (retryable > 0) {
            throw new IllegalStateException(
                    "order " + event.orderId() + " has " + retryable + " notifications left to retry");
        }
    }

    private SendResult sendSafely(final List<NotificationMessage> messages) {
        try {
            return notificationSender.send(messages);
        } catch (final RuntimeException sendFailed) {
            log.warn("order notification send failed size={} cause={}", messages.size(), sendFailed.toString());
            return SendResult.allFailed(messages);
        }
    }

    private static NotificationMessage toMessage(final PendingOrderNotification notification, final OrderStatusNotified event) {
        return NotificationMessage.order(
                notification.id(),
                notification.memberId(),
                event.listingId(),
                new NotificationMessage.Order(
                        event.orderId(),
                        notification.kind().name(),
                        notification.recipient().name(),
                        notification.kind().headline(),
                        event.listingTitle()
                )
        );
    }
}
