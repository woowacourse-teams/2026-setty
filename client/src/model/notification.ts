export type NotificationType = 'KEYWORD' | 'UNKNOWN';

/** 결제 대기와 배차는 구매자가 살 수 없다는 점이 같아 거래중으로 묶는다. */
export type NotifiedListingStatus = 'AVAILABLE' | 'TRADING' | 'SOLD' | 'DELETED';

export type Notification = {
    id: number;
    type: NotificationType;
    listingId: number;
    // 매물 레코드가 없으면 null
    title: string | null;
    price: number | null;
    deliveryFee: number | null;
    listingStatus: NotifiedListingStatus;
    read: boolean;
    createdAt: string;
};

/** 모르는 상태나 매물이 없는 알림은 삭제된 매물로 본다. */
export function notifiedListingStatusOf(value: string | null | undefined): NotifiedListingStatus {
    return value === 'AVAILABLE' || value === 'TRADING' || value === 'SOLD' ? value : 'DELETED';
}

/** 더 이상 살 수 없는 매물의 알림은 흐리게 보여 준다. */
export function isNotifiedListingClosed(status: NotifiedListingStatus): boolean {
    return status !== 'AVAILABLE';
}

export function notificationTypeOf(value: string): NotificationType {
    return value === 'KEYWORD' ? value : 'UNKNOWN';
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
