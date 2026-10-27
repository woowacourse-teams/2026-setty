import { initializeApp } from 'firebase/app';
import { deleteToken, getMessaging, getToken, isSupported, onMessage, type Messaging } from 'firebase/messaging';
import { tokenStore } from '../api/http';
import { deviceTokenRepository, notificationRepository } from '../api/repositories';
import { formatWon } from '../model/furniture';
import { routes } from '../navigation';
import { showSnackbar } from '../ui/snackbar';

/**
 * 브라우저 푸시(FCM). 빌드 변수에 Firebase 설정이 없으면 모든 함수가 아무 일도 하지 않는다.
 * 서비스 워커 등록은 Firebase가 기본 범위(/firebase-cloud-messaging-push-scope)로 하므로 루트의 MSW 워커와 겹치지 않는다.
 */

const PUSH_TOKEN_KEY = 'setty:push-token';

export type PushState = 'unsupported' | 'ios-needs-install' | 'default' | 'granted' | 'denied';

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

async function uploadToken(): Promise<void> {
    const target = await messagingOrNull();
    if (!target) return;
    const token = await getToken(target, { vapidKey: __FIREBASE_VAPID_KEY__ });
    if (!token) return;
    await deviceTokenRepository.register(token);
    savePushToken(token);
}

/** 사용자가 버튼을 눌렀을 때만 부른다. 권한 창은 사용자 동작 안에서만 열린다. */
export async function enablePush(): Promise<PushState> {
    const state = await getPushState();
    if (state !== 'default' && state !== 'granted') return state;
    const permission = await Notification.requestPermission();
    if (permission !== 'granted') return permission;
    await uploadToken();
    return 'granted';
}

/** 이미 허용된 브라우저는 앱이 뜰 때 토큰을 조용히 다시 올린다. 실패해도 화면은 그대로 간다. */
export async function syncPushToken(): Promise<void> {
    if (!tokenStore.get()) return;
    try {
        if ((await getPushState()) === 'granted') await uploadToken();
    } catch {
        // 푸시는 부가 기능이라 실패를 알리지 않는다
    }
}

/** 로그아웃 때 로그인 토큰을 지우기 전에 부른다. 서버 등록을 지우려면 로그인 토큰이 필요하다. */
export async function clearPushToken(): Promise<void> {
    const saved = readPushToken();
    if (!saved) return;
    try {
        await deviceTokenRepository.unregister(saved);
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
}

/** 화면이 열려 있을 때 온 알림은 스낵바로 알리고 바로 확인 처리한다. */
export async function listenForegroundMessages(navigate: (path: string) => void): Promise<void> {
    const target = await messagingOrNull();
    if (!target) return;
    onMessage(target, (payload) => {
        const data = payload.data ?? {};
        const listingId = Number(data.listingId);
        const notificationId = Number(data.notificationId);
        if (!Number.isSafeInteger(listingId) || !data.title) return;
        if (Number.isSafeInteger(notificationId)) void notificationRepository.ack(notificationId).catch(() => undefined);
        showSnackbar(`관심 키워드의 새 매물: ${data.title}${priceSuffix(data.price, data.deliveryFee)}`, {
            actionLabel: '보기',
            long: true,
            onAction: () => navigate(routes.furnitureDetail(listingId))
        });
    });
}

function priceSuffix(price?: string, deliveryFee?: string): string {
    const amount = Number(price);
    if (!Number.isFinite(amount) || !price) return '';
    const fee = Number(deliveryFee);
    return ` · ${formatWon(amount)}${deliveryFee && Number.isFinite(fee) ? ` + 배송비 ${formatWon(fee)}` : ''}`;
}

function readPushToken(): string | null {
    try {
        return window.localStorage.getItem(PUSH_TOKEN_KEY);
    } catch {
        return null;
    }
}

function savePushToken(token: string | null) {
    try {
        if (token) {
            window.localStorage.setItem(PUSH_TOKEN_KEY, token);
        } else {
            window.localStorage.removeItem(PUSH_TOKEN_KEY);
        }
    } catch {
        // 저장소를 못 쓰면 다음 시작 때 토큰을 다시 올린다
    }
}
