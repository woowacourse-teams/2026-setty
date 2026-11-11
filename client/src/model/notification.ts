export type NotificationType = 'KEYWORD' | 'ORDER' | 'UNKNOWN';

export type NotificationRecipient = 'BUYER' | 'SELLER';

/** 결제 대기와 배차는 구매자가 살 수 없다는 점이 같아 거래중으로 묶는다. */
export type NotifiedListingStatus = 'AVAILABLE' | 'TRADING' | 'SOLD' | 'DELETED';

/** 키워드 알림과 주문 알림은 id를 따로 매겨 종류와 id를 함께 들고 다닌다. */
export type NotificationRef = {
    type: NotificationType;
    id: number;
};

export type OrderNotice = {
    orderId: number;
    recipient: NotificationRecipient;
    headline: string;
    // 정산 확정 알림만 금액이 있다
    amount: number | null;
};

export type Notification = NotificationRef & {
    listingId: number;
    // 매물 레코드가 없으면 null
    title: string | null;
    price: number | null;
    deliveryFee: number | null;
    listingStatus: NotifiedListingStatus;
    read: boolean;
    createdAt: string;
    // 주문 알림만 있다
    order: OrderNotice | null;
};

/** 모르는 상태나 매물이 없는 알림은 삭제된 매물로 본다. */
export function notifiedListingStatusOf(value: string | null | undefined): NotifiedListingStatus {
    return value === 'AVAILABLE' || value === 'TRADING' || value === 'SOLD' ? value : 'DELETED';
}

/** 더 이상 살 수 없는 매물의 알림은 흐리게 보여 준다. */
export function isNotifiedListingClosed(status: NotifiedListingStatus): boolean {
    return status !== 'AVAILABLE';
}

export function notificationTypeOf(value: string | null | undefined): NotificationType {
    return value === 'KEYWORD' || value === 'ORDER' ? value : 'UNKNOWN';
}

/** 주문 알림 필드가 하나라도 빠지면 주문 알림으로 다루지 않는다. */
export function orderNoticeOf(
    orderId: number | null | undefined,
    recipient: string | null | undefined,
    headline: string | null | undefined,
    amount: number | null | undefined = null
): OrderNotice | null {
    if (typeof orderId !== 'number' || !Number.isSafeInteger(orderId) || !headline) return null;
    if (recipient !== 'BUYER' && recipient !== 'SELLER') return null;
    return { orderId, recipient, headline, amount: typeof amount === 'number' ? amount : null };
}

/** 구매자는 주문 상세로, 판매자와 키워드 알림은 매물 상세로 간다. */
export function notificationPath(listingId: number, order: OrderNotice | null): string {
    if (order?.recipient === 'BUYER') return `/me/orders/${order.orderId}`;
    return `/listings/${listingId}`;
}

/** 알림 시각을 "방금 전·n분 전·n시간 전·n일 전·M.D"로 보여준다. */
export function formatNotifiedAt(createdAt: string, now: Date = new Date()): string {
    const elapsedMinutes = Math.floor((now.getTime() - new Date(createdAt).getTime()) / 60_000);
    if (Number.isNaN(elapsedMinutes) || elapsedMinutes < 1) return '방금 전';
    if (elapsedMinutes < 60) return `${elapsedMinutes}분 전`;
    if (elapsedMinutes < 60 * 24) return `${Math.floor(elapsedMinutes / 60)}시간 전`;
    if (elapsedMinutes < 60 * 24 * 7) return `${Math.floor(elapsedMinutes / (60 * 24))}일 전`;
    const date = new Date(createdAt);
    return `${date.getMonth() + 1}.${date.getDate()}`;
}
