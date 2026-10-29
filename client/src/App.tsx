import { useLayoutEffect, type ReactNode } from 'react';
import { createBrowserRouter, Navigate, Outlet, ScrollRestoration, useLocation } from 'react-router';
import { deliveryTokenStore, tokenStore } from './api/http';
import { DemoScreen } from './features/demo/DemoScreen';
import { DriverRequestDetailScreen } from './features/driver/DriverRequestDetailScreen';
import { DriverRequestsScreen } from './features/driver/DriverRequestsScreen';
import { DriverMyPageScreen } from './features/driver/DriverMyPageScreen';
import { DriverShipmentDetailScreen } from './features/driver/DriverShipmentDetailScreen';
import { DriverShipmentsScreen } from './features/driver/DriverShipmentsScreen';
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
import { loginStateFrom, routes } from './navigation';
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

/** 로그인 토큰이 없으면 로그인 화면으로 보내고, 로그인한 뒤 이 화면으로 돌아오게 한다. */
function RequireLogin({ children }: { children: ReactNode }) {
    const location = useLocation();
    if (!tokenStore.get()) return <Navigate replace state={loginStateFrom(location)} to={routes.login} />;
    return children;
}

/** 배송원 토큰이 없으면 배송원 로그인으로 보낸다. 회원 로그인과는 따로 확인한다. */
function RequireDriverLogin({ children }: { children: ReactNode }) {
    if (!deliveryTokenStore.get()) return <Navigate replace to={routes.driverLogin} />;
    return children;
}

export const router = createBrowserRouter([
    // 데모 화면은 구매자·배송원 화면을 나란히 띄우므로 휴대폰 폭으로 제한하는 AppShell 밖에 둔다
    { path: routes.demo, Component: DemoScreen },
    {
        Component: AppShell,
        children: [
            { path: routes.login, Component: LoginScreen },
            { path: routes.signUp, Component: SignUpScreen },
            // 매물 목록·상세는 로그인 없이 둘러볼 수 있게 열고, 찜·구매처럼 로그인이 필요할 때 로그인으로 보낸다
            { index: true, Component: HomeScreen },
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
            { path: routes.driverRequests, element: <RequireDriverLogin><DriverRequestsScreen /></RequireDriverLogin> },
            { path: '/driver/requests/:deliveryId', element: <RequireDriverLogin><DriverRequestDetailScreen /></RequireDriverLogin> },
            { path: routes.driverShipments, element: <RequireDriverLogin><DriverShipmentsScreen /></RequireDriverLogin> },
            { path: '/driver/shipments/:deliveryId', element: <RequireDriverLogin><DriverShipmentDetailScreen /></RequireDriverLogin> },
            { path: routes.driverMyPage, element: <RequireDriverLogin><DriverMyPageScreen /></RequireDriverLogin> },
            { path: '*', element: <Navigate replace to={routes.home} /> }
        ]
    }
]);
