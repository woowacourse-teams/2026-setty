package setty.notification.order.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import setty.common.OrderStatusNotified;
import setty.common.SettlementConfirmed;
import setty.notification.send.NotificationMessage;
import setty.notification.send.NotificationSendConfiguration;
import setty.notification.send.NotificationSender;
import setty.notification.send.SendResult;
import setty.platform.NotificationListingReader;
import setty.platform.NotifiedListing;

// 받는 사람이 한두 명이라 배치 없이 바로 보낸다. 전용 실행기에서 돌아 키워드 대량 발송 뒤에 밀리지도,
// FCM 응답(최대 10초)을 기다리는 동안 결제 확정·배차 같은 주문·배송 리스너의 공용 실행기를 막지도 않는다.
// 행 저장과 발송을 한 트랜잭션에 묶지 않아 발송 실패가 저장을 되돌리지 않는다.
// 재시도할 알림이 남으면 예외로 발행 기록을 남겨 재발행 스케줄러가 다시 부르게 한다.
@Component
public class OrderNotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OrderNotificationDispatcher.class);

    private final OrderNotificationService orderNotificationService;
    private final NotificationSender notificationSender;
    private final NotificationListingReader notificationListingReader;

    public OrderNotificationDispatcher(
            final OrderNotificationService orderNotificationService,
            final NotificationSender notificationSender,
            final NotificationListingReader notificationListingReader
    ) {
        this.orderNotificationService = orderNotificationService;
        this.notificationSender = notificationSender;
        this.notificationListingReader = notificationListingReader;
    }

    @Async(NotificationSendConfiguration.ORDER_DISPATCH_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderStatusNotified(final OrderStatusNotified event) {
        dispatch(event);
    }

    @Async(NotificationSendConfiguration.ORDER_DISPATCH_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSettlementConfirmed(final SettlementConfirmed event) {
        dispatch(event);
    }

    public void dispatch(final OrderStatusNotified event) {
        send(event.orderId(), event.listingId(), orderNotificationService.createFor(event), event.listingTitle());
    }

    public void dispatch(final SettlementConfirmed event) {
        final List<PendingOrderNotification> pending = orderNotificationService.createFor(event);
        if (pending.isEmpty()) {
            return;
        }
        send(event.orderId(), event.listingId(), pending, settlementBody(event));
    }

    private void send(
            final Long orderId,
            final Long listingId,
            final List<PendingOrderNotification> pending,
            final String body
    ) {
        if (pending.isEmpty()) {
            return;
        }
        final List<NotificationMessage> messages = pending.stream()
                .map(notification -> toMessage(notification, orderId, listingId, body))
                .toList();
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
                    "order " + orderId + " has " + retryable + " notifications left to retry");
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

    private String settlementBody(final SettlementConfirmed event) {
        final NotifiedListing listing = notificationListingReader.findAll(List.of(event.listingId())).get(event.listingId());
        final String amount = String.format(Locale.KOREA, "%,d원", event.amount());
        return listing == null ? amount : listing.title() + " · " + amount;
    }

    private static NotificationMessage toMessage(
            final PendingOrderNotification notification,
            final Long orderId,
            final Long listingId,
            final String body
    ) {
        return NotificationMessage.order(
                notification.id(),
                notification.memberId(),
                listingId,
                new NotificationMessage.Order(
                        orderId,
                        notification.kind().name(),
                        notification.recipient().name(),
                        notification.kind().headline(),
                        body
                )
        );
    }
}
