import { useCallback } from 'react';
import { useLocation, useNavigate, type Location } from 'react-router';
import { resetUser } from './analytics/posthog';
import { deliveryLoginIdStore, deliveryTokenStore, tokenStore } from './api/http';
import { resetHomeStore } from './features/home/homeStore';

export const routes = {
    login: '/login',
    signUp: '/signup',
    home: '/',
    furnitureDetail: (furnitureId: number) => `/listings/${furnitureId}`,
    myPage: '/me',
    profileEdit: '/me/profile',
    myListings: '/me/listings',
    listingCreate: '/me/listings/new',
    listingEdit: (listingId: number) => `/me/listings/${listingId}/edit`,
    favorites: '/me/favorites',
    orders: '/me/orders',
    orderDetail: (orderId: number) => `/me/orders/${orderId}`,
    keywords: '/me/keywords',
    keywordConditions: (keywordId: number) => `/me/keywords/${keywordId}`,
    notifications: '/me/notifications',
    payment: (listingId: number) => `/payment?listingId=${listingId}`,
    paymentComplete: '/payment/complete',
    driverLogin: '/login?role=driver',
    driverSignUp: '/signup?role=driver',
    driverRequests: '/driver',
    driverRequestDetail: (deliveryId: number) => `/driver/requests/${deliveryId}`,
    driverShipments: '/driver/shipments',
    driverShipmentDetail: (deliveryId: number) => `/driver/shipments/${deliveryId}`,
    driverMyPage: '/driver/me',
    demo: '/demo'
} as const;

/** 로그인·회원가입에서 고르는 계정 종류. 주소의 `?role=driver`로 배송원을 미리 고를 수 있다. */
export type AccountRole = 'member' | 'driver';

export function accountRoleOf(search: string): AccountRole {
    return new URLSearchParams(search).get('role') === 'driver' ? 'driver' : 'member';
}

/**
 * 앱의 뒤로 가기. 이 사이트 안에서 쌓인 기록이 있으면 돌아가고,
 * 주소로 바로 들어와 돌아갈 화면이 없으면 홈(배송원 화면은 [fallback])으로 보낸다.
 */
export function useNavigateBack(fallback: string = routes.home) {
    const navigate = useNavigate();
    return useCallback(() => {
        const historyIndex = (window.history.state as { idx?: number } | null)?.idx ?? 0;
        if (historyIndex > 0) {
            navigate(-1);
        } else {
            navigate(fallback, { replace: true });
        }
    }, [navigate, fallback]);
}

/** 로그인한 뒤 돌아갈 화면. 결제처럼 주소 밖 state가 필요한 화면도 그대로 되살린다. */
type LoginReturnTo = Pick<Location, 'pathname' | 'search' | 'state'>;

/** 로그인 화면으로 넘길 location state. 로그인한 뒤 [location]으로 돌아온다. */
export function loginStateFrom(location: LoginReturnTo): { from: LoginReturnTo } {
    return { from: { pathname: location.pathname, search: location.search, state: location.state } };
}

/** 로그인 화면이 받은 state에서 로그인한 뒤 돌아갈 화면을 꺼낸다. 없으면 null. */
export function loginReturnToOf(state: unknown): LoginReturnTo | null {
    if (typeof state !== 'object' || state === null || !('from' in state)) return null;
    const from = state.from as LoginReturnTo | null;
    return from && typeof from.pathname === 'string' ? from : null;
}

/** 로그인이 필요한 동작을 하면 보던 화면을 기억한 채 로그인으로 보낸다. 로그인 화면에서 뒤로 가면 보던 화면으로 돌아온다. */
export function useNavigateToLoginFromHere() {
    const navigate = useNavigate();
    const location = useLocation();
    return useCallback(() => {
        navigate(routes.login, { state: loginStateFrom(location) });
    }, [navigate, location]);
}

/** 로그아웃하거나 토큰이 만료되면 보던 화면을 버리고 로그인으로 보낸다. */
export function useNavigateToLogin() {
    const navigate = useNavigate();
    return useCallback(() => {
        tokenStore.clear();
        resetUser();
        resetHomeStore();
        navigate(routes.login, { replace: true });
    }, [navigate]);
}

/** 배송원 토큰이 만료되거나 로그아웃하면 배송원 로그인으로 보낸다. 회원 로그인은 그대로 둔다. */
export function useNavigateToDriverLogin() {
    const navigate = useNavigate();
    return useCallback(() => {
        deliveryTokenStore.clear();
        deliveryLoginIdStore.clear();
        navigate(routes.driverLogin, { replace: true });
    }, [navigate]);
}
