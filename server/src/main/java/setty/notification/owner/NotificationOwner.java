package setty.notification.owner;

import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;

// 회원과 기사의 ID는 각자 1부터 늘어 겹치므로 숫자 ID만으로는 알림 받을 사람을 가를 수 없다. 늘 종류와 함께 다룬다.
public record NotificationOwner(OwnerType type, Long id) {

    public NotificationOwner {
        if (type == null || id == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

    public static NotificationOwner member(final Long memberId) {
        return new NotificationOwner(OwnerType.MEMBER, memberId);
    }

    public static NotificationOwner driver(final Long driverId) {
        return new NotificationOwner(OwnerType.DRIVER, driverId);
    }
}
