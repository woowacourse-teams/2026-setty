import { useCallback } from 'react';
import { useNavigate } from 'react-router';
import { tokenStore } from './api/http';
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
    payment: (listingId: number) => `/payment?listingId=${listingId}`
} as const;

/**
 * 앱의 뒤로 가기. 이 사이트 안에서 쌓인 기록이 있으면 돌아가고,
 * 주소로 바로 들어와 돌아갈 화면이 없으면 홈으로 보낸다.
 */
export function useNavigateBack() {
    const navigate = useNavigate();
    return useCallback(() => {
        const historyIndex = (window.history.state as { idx?: number } | null)?.idx ?? 0;
        if (historyIndex > 0) {
            navigate(-1);
        } else {
            navigate(routes.home, { replace: true });
        }
    }, [navigate]);
}

/** 로그아웃하거나 토큰이 만료되면 보던 화면을 버리고 로그인으로 보낸다. */
export function useNavigateToLogin() {
    const navigate = useNavigate();
    return useCallback(() => {
        tokenStore.clear();
        resetHomeStore();
        navigate(routes.login, { replace: true });
    }, [navigate]);
}
