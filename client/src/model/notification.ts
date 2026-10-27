export type NotificationType = 'KEYWORD' | 'UNKNOWN';

export type Notification = {
    id: number;
    type: NotificationType;
    listingId: number;
    // 매물 레코드가 없으면 null
    title: string | null;
    price: number | null;
    deliveryFee: number | null;
    createdAt: string;
};

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
