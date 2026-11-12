import { initializeApp } from 'firebase/app';
import { deleteToken, getMessaging, getToken, isSupported, onMessage, type Messaging } from 'firebase/messaging';
import { deliveryRepository } from '../api/deliveryRepository';
import { deliveryTokenStore, tokenStore } from '../api/http';
import { deviceTokenRepository, notificationRepository } from '../api/repositories';
import { formatWon } from '../model/furniture';
import { driverNoticeOf, notificationPath, orderNoticeOf } from '../model/notification';
import { routes } from '../navigation';
import { showSnackbar } from '../ui/snackbar';
import { refreshUnreadNotifications } from './unreadNotifications';

/**
 * 브라우저 푸시(FCM). 빌드 변수에 Firebase 설정이 없으면 모든 함수가 아무 일도 하지 않는다.
 * 서비스 워커 등록은 Firebase가 기본 범위(/firebase-cloud-messaging-push-scope)로 하므로 루트의 MSW 워커와 겹치지 않는다.
 */

const PUSH_TOKEN_KEY = 'setty:push-token';
const PUSH_ACCOUNT_KEY = 'setty:push-account';

export type PushState = 'unsupported' | 'ios-needs-install' | 'default' | 'granted' | 'denied';

/**
 * 브라우저 토큰 하나는 회원이나 기사 한쪽에만 등록된다. 마지막으로 알림을 켜거나 로그인한 쪽이 주인이 되고,
 * 앱을 다시 열면 그 주인으로 토큰을 다시 올린다.
 */
export type PushAccount = 'member' | 'driver';

const accounts: Record<PushAccount, {
    isLoggedIn: () => boolean;
    register: (token: string) => Promise<void>;
    unregister: (token: string) => Promise<void>;
}> = {
    member: {
        isLoggedIn: () => Boolean(tokenStore.get()),
        register: (token) => deviceTokenRepository.register(token),
        unregister: (token) => deviceTokenRepository.unregister(token)
    },
    driver: {
        isLoggedIn: () => Boolean(deliveryTokenStore.get()),
        register: (token) => deliveryRepository.registerDeviceToken(token),
        unregister: (token) => deliveryRepository.unregisterDeviceToken(token)
    }
};

export function isPushConfigured(): boolean {
    return __FIREBASE_CONFIG__ !== null && __FIREBASE_VAPID_KEY__ !== '';
}

/** 아이폰 사파리는 홈 화면에 추가한 앱에서만 푸시가 된다. */
function isIosBrowserTab(): boolean {
    const isIos = /iPhone|iPad|iPod/.test(navigator.userAgent)
        || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
    const isStandalone = window.matchMedia('(display-mode: standalone)').matches
        || (navigator as { standalone?: boolean }).standalone === true;
    return isIos && !isStandalone;
}

export async function getPushState(): Promise<PushState> {
    if (!isPushConfigured()) return 'unsupported';
    if (isIosBrowserTab()) return 'ios-needs-install';
    if (!('Notification' in window) || !(await isSupported())) return 'unsupported';
    return Notification.permission;
}

let messaging: Messaging | null = null;

async function messagingOrNull(): Promise<Messaging | null> {
    if (!isPushConfigured() || !(await isSupported())) return null;
    if (!messaging) messaging = getMessaging(initializeApp(__FIREBASE_CONFIG__!));
    return messaging;
}

async function uploadToken(account: PushAccount): Promise<void> {
    const target = await messagingOrNull();
    if (!target) return;
    const token = await getToken(target, { vapidKey: __FIREBASE_VAPID_KEY__ });
    if (!token) return;
    await accounts[account].register(token);
    savePushToken(token);
    saveStored(PUSH_ACCOUNT_KEY, account);
}

/** 사용자가 버튼을 눌렀을 때만 부른다. 권한 창은 사용자 동작 안에서만 열린다. */
export async function enablePush(account: PushAccount): Promise<PushState> {
    const state = await getPushState();
    if (state !== 'default' && state !== 'granted') return state;
    const permission = await Notification.requestPermission();
    if (permission !== 'granted') return permission;
    await uploadToken(account);
    return 'granted';
}

/** 이미 허용된 브라우저는 앱이 뜰 때·로그인할 때 토큰을 조용히 다시 올린다. 실패해도 화면은 그대로 간다. */
export async function syncPushToken(account: PushAccount = savedPushAccount()): Promise<void> {
    if (!accounts[account].isLoggedIn()) return;
    try {
        if ((await getPushState()) === 'granted') await uploadToken(account);
    } catch (error) {
        // 푸시는 부가 기능이라 화면에는 알리지 않고 원인만 남긴다
        console.warn('[push] 토큰 등록 실패', error);
    }
}

/**
 * 로그아웃 때 로그인 토큰을 지우기 전에 부른다. 서버 등록을 지우려면 로그인 토큰이 필요하다.
 * 브라우저 토큰이 다른 쪽(회원·기사) 것이면 그쪽 알림이 끊기지 않게 그대로 둔다.
 */
export async function clearPushToken(account: PushAccount = 'member'): Promise<void> {
    const saved = readPushToken();
    if (!saved || savedPushAccount() !== account) return;
    try {
        await accounts[account].unregister(saved);
    } catch {
        // 서버에 못 지워도 브라우저 토큰은 폐기한다
    }
    try {
        const target = await messagingOrNull();
        if (target) await deleteToken(target);
    } catch {
        // 이미 폐기된 토큰이면 넘어간다
    }
    savePushToken(null);
    saveStored(PUSH_ACCOUNT_KEY, null);
}

// 서비스 워커(firebase-messaging-sw.js)가 알림을 누른 탭에 보내는 메시지 종류
const OPEN_LISTING_MESSAGE = 'setty:open-listing';

/** 알림을 눌렀을 때 워커가 보낸 매물 주소로 이동한다. 워커가 이 탭을 관리하지 않아 직접 이동시킬 수 없다. */
export function listenNotificationClicks(navigate: (path: string) => void) {
    if (!('serviceWorker' in navigator)) return;
    navigator.serviceWorker.addEventListener('message', (event: MessageEvent) => {
        const data = event.data as { type?: string; path?: string } | null;
        if (data?.type === OPEN_LISTING_MESSAGE && typeof data.path === 'string' && data.path.startsWith('/')) {
            navigate(data.path);
        }
    });
}

/** 화면이 열려 있을 때 온 알림은 스낵바로 알리고 바로 확인 처리한다. */
export async function listenForegroundMessages(navigate: (path: string) => void): Promise<void> {
    const target = await messagingOrNull();
    if (!target) return;
    onMessage(target, (payload) => {
        const data = payload.data ?? {};
        const driverNotice = driverNoticeOf(data);
        if (driverNotice) {
            showSnackbar(driverNotice.body ? `${driverNotice.headline} · ${driverNotice.body}` : driverNotice.headline, {
                actionLabel: '보기',
                long: true,
                onAction: () => navigate(driverNotice.path)
            });
            return;
        }
        const listingId = Number(data.listingId);
        const notificationId = Number(data.notificationId);
        if (!Number.isSafeInteger(listingId)) return;
        if (data.type === 'ORDER') {
            showOrderSnackbar(data, listingId, notificationId, navigate);
            return;
        }
        if (!data.title) return;
        if (Number.isSafeInteger(notificationId)) {
            void notificationRepository.ack({ type: 'KEYWORD', id: notificationId }).catch(() => undefined);
        }
        void refreshUnreadNotifications();
        showSnackbar(`관심 키워드의 새 매물: ${data.title}${priceSuffix(data.price, data.deliveryFee)}`, {
            actionLabel: '보기',
            long: true,
            onAction: () => navigate(routes.furnitureDetail(listingId))
        });
    });
}

function showOrderSnackbar(
    data: Record<string, string>,
    listingId: number,
    notificationId: number,
    navigate: (path: string) => void
) {
    const order = orderNoticeOf(Number(data.orderId), data.recipient, data.headline);
    if (!order) return;
    if (Number.isSafeInteger(notificationId)) {
        void notificationRepository.ack({ type: 'ORDER', id: notificationId }).catch(() => undefined);
    }
    void refreshUnreadNotifications();
    showSnackbar(data.body ? `${order.headline} · ${data.body}` : order.headline, {
        actionLabel: '보기',
        long: true,
        onAction: () => navigate(notificationPath(listingId, order))
    });
}

function priceSuffix(price?: string, deliveryFee?: string): string {
    const amount = Number(price);
    if (!Number.isFinite(amount) || !price) return '';
    const fee = Number(deliveryFee);
    return ` · ${formatWon(amount)}${deliveryFee && Number.isFinite(fee) ? ` + 배송비 ${formatWon(fee)}` : ''}`;
}

function savedPushAccount(): PushAccount {
    return readStored(PUSH_ACCOUNT_KEY) === 'driver' ? 'driver' : 'member';
}

function readPushToken(): string | null {
    return readStored(PUSH_TOKEN_KEY);
}

function savePushToken(token: string | null) {
    saveStored(PUSH_TOKEN_KEY, token);
}

function readStored(key: string): string | null {
    try {
        return window.localStorage.getItem(key);
    } catch {
        return null;
    }
}

function saveStored(key: string, value: string | null) {
    try {
        if (value) {
            window.localStorage.setItem(key, value);
        } else {
            window.localStorage.removeItem(key);
        }
    } catch {
        // 저장소를 못 쓰면 다음 시작 때 토큰을 다시 올린다
    }
}
