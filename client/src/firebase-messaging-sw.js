// FCM 백그라운드 서비스 워커. webpack이 루트의 firebase-messaging-sw.js로 내보낸다.
// 서버는 data 메시지(notificationId·listingId·title·price·deliveryFee)만 보내므로 알림 표시는 여기서 한다.
import { initializeApp } from 'firebase/app';
import { getMessaging, onBackgroundMessage } from 'firebase/messaging/sw';

const NOTIFICATION_TITLE = '관심 키워드의 새 매물';
const ICON_URL = '/setty-apple-touch-icon.png';

function formatWon(value) {
    const amount = Number(value);
    return Number.isFinite(amount) && value !== undefined && value !== '' ? `${amount.toLocaleString('ko-KR')}원` : null;
}

function bodyOf(data) {
    const price = formatWon(data.price);
    const deliveryFee = formatWon(data.deliveryFee);
    const priceLine = price ? (deliveryFee ? `${price} + 배송비 ${deliveryFee}` : price) : null;
    return [data.title, priceLine].filter(Boolean).join('\n');
}

function detailUrl(data) {
    const query = data.notificationId ? `?notificationId=${encodeURIComponent(data.notificationId)}` : '';
    return `/listings/${encodeURIComponent(data.listingId)}${query}`;
}

if (__FIREBASE_CONFIG__) {
    const messaging = getMessaging(initializeApp(__FIREBASE_CONFIG__));
    onBackgroundMessage(messaging, (payload) => {
        const data = payload.data ?? {};
        if (!data.listingId || !data.title) return undefined;
        return self.registration.showNotification(NOTIFICATION_TITLE, {
            body: bodyOf(data),
            icon: ICON_URL,
            tag: `listing-${data.listingId}`,
            data: { url: detailUrl(data) }
        });
    });
}

// 알림을 누르면 열려 있는 세티 탭을 앞으로 가져와 매물 상세로 보내고, 없으면 새 창으로 연다.
// 이 워커는 Firebase 전용 범위에 등록돼 세티 탭을 관리하지 않으므로 client.navigate()를 쓸 수 없다.
// 그래서 탭에 메시지를 보내 페이지가 직접 이동한다. 상세 화면이 notificationId로 확인 처리한다.
export const OPEN_LISTING_MESSAGE = 'setty:open-listing';

async function openListing(path) {
    const windows = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
    const client = windows.find((candidate) => new URL(candidate.url).origin === self.location.origin);
    if (!client) {
        await self.clients.openWindow(path);
        return;
    }
    await client.focus();
    client.postMessage({ type: OPEN_LISTING_MESSAGE, path });
}

self.addEventListener('notificationclick', (event) => {
    event.notification.close();
    event.waitUntil(openListing(event.notification.data?.url ?? '/'));
});
