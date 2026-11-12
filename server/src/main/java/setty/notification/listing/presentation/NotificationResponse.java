package setty.notification.listing.presentation;

import java.time.Instant;
import setty.notification.listing.domain.ListingNotification;
import setty.notification.order.domain.OrderNotification;

// 주문 알림은 kind·orderId·recipient·headline을 채우고 가격·배송비는 비운다. 키워드 알림은 그 반대다.
public record NotificationResponse(
        Long id,
        String type,
        Long listingId,
        String title,
        Integer price,
        Integer deliveryFee,
        String listingStatus,
        String status,
        boolean read,
        Instant createdAt,
        String kind,
        Long orderId,
        String recipient,
        String headline,
        Integer amount
) {

    static final String KEYWORD = "KEYWORD";
    static final String ORDER = "ORDER";

    public static NotificationResponse of(
            final ListingNotification notification,
            final String title,
            final Integer price,
            final Integer deliveryFee,
            final String listingStatus
    ) {
        return new NotificationResponse(
                notification.getId(),
                KEYWORD,
                notification.getListingId(),
                title,
                price,
                deliveryFee,
                listingStatus,
                notification.getStatus().name(),
                notification.isRead(),
                notification.getCreatedAt(),
                null,
                null,
                null,
                null,
                null
        );
    }

    public static NotificationResponse of(
            final OrderNotification notification,
            final String title,
            final String listingStatus
    ) {
        return new NotificationResponse(
                notification.getId(),
                ORDER,
                notification.getListingId(),
                title,
                null,
                null,
                listingStatus,
                notification.getStatus().name(),
                notification.isRead(),
                notification.getCreatedAt(),
                notification.getKind().name(),
                notification.getOrderId(),
                notification.getRecipient().name(),
                notification.getKind().headline(),
                notification.getAmount()
        );
    }
}
