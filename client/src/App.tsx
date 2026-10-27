import { useLayoutEffect, type ReactNode } from 'react';
import { createBrowserRouter, Navigate, Outlet, ScrollRestoration, useLocation } from 'react-router';
import { tokenStore } from './api/http';
import { FavoritesScreen } from './features/favorites/FavoritesScreen';
import { HomeScreen } from './features/home/HomeScreen';
import { KeywordConditionsScreen } from './features/keywords/KeywordConditionsScreen';
import { KeywordsScreen } from './features/keywords/KeywordsScreen';
import { FurnitureDetailScreen } from './features/listingdetail/FurnitureDetailScreen';
import { ListingEditorScreen } from './features/listingeditor/ListingEditorScreen';
import { LoginScreen } from './features/login/LoginScreen';
import { MyListingsScreen } from './features/mylistings/MyListingsScreen';
import { MyPageScreen } from './features/mypage/MyPageScreen';
import { NotificationsScreen } from './features/notifications/NotificationsScreen';
import { OrderDetailScreen } from './features/orders/OrderDetailScreen';
import { OrdersScreen } from './features/orders/OrdersScreen';
import { PaymentCompleteScreen } from './features/payment/PaymentCompleteScreen';
import { PaymentReturnScreen } from './features/payment/PaymentReturnScreen';
import { PaymentScreen } from './features/payment/PaymentScreen';
import { ProfileEditScreen } from './features/profile/ProfileEditScreen';
import { SignUpScreen } from './features/signup/SignUpScreen';
import { routes } from './navigation';
import { onScreenChanged } from './ui/snackbar';
import componentStyles from './ui/components.module.css';

function AppShell() {
    const { pathname } = useLocation();
    // 새 화면이 진입하며 띄우는 안내보다 먼저 이전 화면의 안내를 닫도록 레이아웃 단계에서 처리한다
    useLayoutEffect(onScreenChanged, [pathname]);
    return (
        <div className={componentStyles.app}>
            <Outlet />
            {/* 뒤로 가면 보던 위치로, 새 화면은 맨 위에서 시작한다 */}
            <ScrollRestoration />
        </div>
    );
}

/** 로그인 토큰이 없으면 로그인 화면으로 보낸다. 앱도 로그인 화면에서 시작한다. */
function RequireLogin({ children }: { children: ReactNode }) {
    if (!tokenStore.get()) return <Navigate replace to={routes.login} />;
    return children;
}

export const router = createBrowserRouter([
    {
        Component: AppShell,
        children: [
            { path: routes.login, Component: LoginScreen },
            { path: routes.signUp, Component: SignUpScreen },
            { index: true, element: <RequireLogin><HomeScreen /></RequireLogin> },
            // 매물 상세는 공유한 주소로 바로 들어올 수 있게 로그인 없이 연다
            { path: '/listings/:listingId', Component: FurnitureDetailScreen },
            { path: routes.myPage, element: <RequireLogin><MyPageScreen /></RequireLogin> },
            { path: routes.profileEdit, element: <RequireLogin><ProfileEditScreen /></RequireLogin> },
            { path: routes.myListings, element: <RequireLogin><MyListingsScreen /></RequireLogin> },
            { path: routes.listingCreate, element: <RequireLogin><ListingEditorScreen /></RequireLogin> },
            { path: '/me/listings/:listingId/edit', element: <RequireLogin><ListingEditorScreen /></RequireLogin> },
            { path: routes.favorites, element: <RequireLogin><FavoritesScreen /></RequireLogin> },
            { path: routes.orders, element: <RequireLogin><OrdersScreen /></RequireLogin> },
            { path: '/me/orders/:orderId', element: <RequireLogin><OrderDetailScreen /></RequireLogin> },
            { path: routes.keywords, element: <RequireLogin><KeywordsScreen /></RequireLogin> },
            { path: '/me/keywords/:keywordId', element: <RequireLogin><KeywordConditionsScreen /></RequireLogin> },
            { path: routes.notifications, element: <RequireLogin><NotificationsScreen /></RequireLogin> },
            { path: '/payment', element: <RequireLogin><PaymentScreen /></RequireLogin> },
            { path: '/payment/success', element: <RequireLogin><PaymentReturnScreen result="success" /></RequireLogin> },
            { path: '/payment/fail', element: <RequireLogin><PaymentReturnScreen result="fail" /></RequireLogin> },
            { path: routes.paymentComplete, element: <RequireLogin><PaymentCompleteScreen /></RequireLogin> },
            { path: '*', element: <Navigate replace to={routes.home} /> }
        ]
    }
]);
