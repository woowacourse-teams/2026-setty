import { useCallback } from 'react';
import { useNavigate } from 'react-router';
import { resetUser } from './analytics/posthog';
import { deliveryTokenStore, tokenStore } from './api/http';
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
    driverSettlement: '/driver/settlement',
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
        navigate(routes.driverLogin, { replace: true });
    }, [navigate]);
}
