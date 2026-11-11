package setty.notification.order.domain;

import static setty.notification.order.domain.OrderNotificationRecipient.BUYER;
import static setty.notification.order.domain.OrderNotificationRecipient.DRIVER;
import static setty.notification.order.domain.OrderNotificationRecipient.SELLER;

import java.util.Arrays;
import java.util.List;
import setty.common.OrderStatusNotified.Change;

// SALE_COMPLETED는 금액이 있는 SETTLEMENT_CONFIRMED로 대체해 더 만들지 않는다. 지난 알림을 보여 주려 값은 남긴다.
// SETTLEMENT_CONFIRMED·DRIVER_SETTLEMENT_CONFIRMED는 주문 상태가 아니라 정산 확정 이벤트로 만들어져 change가 없다.
public enum OrderNotificationKind {
    ORDER_CONFIRMED(Change.CONFIRMED, List.of(BUYER), "결제가 끝났어요. 기사를 찾고 있어요"),
    LISTING_SOLD(Change.CONFIRMED, List.of(SELLER), "내 물건이 팔렸어요"),
    DELIVERY_ACCEPTED(Change.DELIVERY_ACCEPTED, List.of(BUYER, SELLER), "기사가 배정됐어요"),
    DELIVERY_PICKED_UP(Change.DELIVERY_PICKED_UP, List.of(BUYER), "기사가 물건을 실었어요"),
    DELIVERY_DELIVERED(Change.DELIVERY_DELIVERED, List.of(BUYER, SELLER), "배송이 끝났어요"),
    PURCHASE_COMPLETED(Change.COMPLETED, List.of(BUYER), "구매가 확정됐어요"),
    SALE_COMPLETED(Change.COMPLETED, List.of(), "판매가 확정됐어요"),
    ORDER_CANCELLED(Change.CANCELLED_BY_BUYER, List.of(BUYER), "주문이 취소됐어요"),
    ORDER_CANCELLED_BY_BUYER(Change.CANCELLED_BY_BUYER, List.of(SELLER), "구매자가 주문을 취소해 다시 판매 중이에요"),
    ORDER_CANCELLATION_REJECTED(Change.CANCELLATION_REJECTED, List.of(BUYER), "기사가 이미 배정돼 취소할 수 없어요"),
    ORDER_CANCELLED_BY_SELLER(Change.CANCELLED_BY_SELLER, List.of(BUYER), "판매자가 판매를 중단해 주문이 취소됐어요"),
    SETTLEMENT_CONFIRMED(null, List.of(SELLER), "정산이 확정됐어요"),
    DRIVER_SETTLEMENT_CONFIRMED(null, List.of(DRIVER), "배송비 정산이 확정됐어요");

    private final Change change;
    private final List<OrderNotificationRecipient> recipients;
    private final String headline;

    OrderNotificationKind(final Change change, final List<OrderNotificationRecipient> recipients, final String headline) {
        this.change = change;
        this.recipients = recipients;
        this.headline = headline;
    }

    public static List<OrderNotificationKind> of(final Change change) {
        return Arrays.stream(values()).filter(kind -> kind.change == change).toList();
    }

    public List<OrderNotificationRecipient> recipients() {
        return recipients;
    }

    public String headline() {
        return headline;
    }
}
