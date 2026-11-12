package setty.notification.order.domain;

import setty.notification.owner.NotificationOwner;
import setty.notification.owner.OwnerType;

// 알림에서 맡은 역할이다. 누르면 갈 화면을 고르고, 받는 사람이 회원인지 기사인지를 정한다.
public enum OrderNotificationRecipient {
    BUYER(OwnerType.MEMBER),
    SELLER(OwnerType.MEMBER),
    DRIVER(OwnerType.DRIVER);

    private final OwnerType ownerType;

    OrderNotificationRecipient(final OwnerType ownerType) {
        this.ownerType = ownerType;
    }

    public NotificationOwner ownerOf(final Long id) {
        return new NotificationOwner(ownerType, id);
    }
}
