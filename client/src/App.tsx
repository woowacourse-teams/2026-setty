import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react';
import {
    Navigate,
    Route,
    Routes,
    useBeforeUnload,
    useBlocker,
    useLocation,
    useNavigate,
    useNavigationType,
    useParams,
    type BlockerFunction
} from 'react-router';
import { logout } from './api/auth';
import { fetchListing, type ListingDetail } from './api/listings';
import { AuthModal } from './components/AuthModal';
import { FurnitureRegistration } from './components/FurnitureRegistration';
import { Header } from './components/Header';
import { MockScenarioController } from './components/MockScenarioController';
import { MyAccount } from './components/MyAccount';
import { MyFavorites } from './components/MyFavorites';
import { MyListings } from './components/MyListings';
import { MyOrders } from './components/MyOrders';
import { ProductDetail } from './components/ProductDetail';
import { ProductGrid } from './components/ProductGrid';
import { usePaymentReturn } from './payment/usePaymentReturn';
import productGridStyles from './styles/modules/ProductGrid.module.css';
import layoutStyles from './styles/modules/Layout.module.css';
import paymentStyles from './styles/modules/Payment.module.css';
import './styles/global.css';

type AppLocationState = {
    authModalEntry?: boolean;
    fromHome?: boolean;
    fromMyFavorites?: boolean;
    fromMyListings?: boolean;
    logout?: boolean;
    openAuthFor?: string;
};

type RequireAuthProps = {
    children: ReactNode;
    isLoggedIn: boolean;
};

type EditListingRouteProps = {
    onCancel: () => void;
    onDirtyChange: (isDirty: boolean) => void;
    onSaved: () => void;
};

const unsavedChangesMessage = '작성 중인 내용이 저장되지 않습니다. 나가시겠어요?';

function getLocationState(state: unknown): AppLocationState {
    return state && typeof state === 'object' ? state as AppLocationState : {};
}

function isSafeProtectedPath(path: string | null | undefined): path is string {
    if (!path) return false;

    return path === '/my-listings'
        || path === '/my-orders'
        || path === '/my-account'
        || path === '/my-favorites'
        || path === '/my-listings/new'
        || /^\/my-listings\/\d+\/edit$/.test(path);
}

function getViewName(pathname: string) {
    if (pathname === '/') return 'home';
    if (/^\/listings\/[^/]+$/.test(pathname)) return 'detail';
    if (pathname === '/my-listings') return 'my-listings';
    if (pathname === '/my-orders') return 'my-orders';
    if (pathname === '/my-account') return 'my-account';
    if (pathname === '/my-favorites') return 'my-favorites';
    if (pathname === '/my-listings/new' || /^\/my-listings\/[^/]+\/edit$/.test(pathname)) return 'registration';
    return 'home';
}

function getViewTitle(pathname: string) {
    if (pathname === '/') return '판매 중인 가구';
    if (/^\/listings\/[^/]+$/.test(pathname)) return '매물 상세';
    if (pathname === '/my-listings') return '내 가구';
    if (pathname === '/my-orders') return '내 주문';
    if (pathname === '/my-account') return '내 계정';
    if (pathname === '/my-favorites') return '찜한 매물';
    if (pathname === '/my-listings/new') return '새 가구 등록';
    if (/^\/my-listings\/[^/]+\/edit$/.test(pathname)) return '가구 수정';
    return '판매 중인 가구';
}

function removeAuthSearch(pathname: string, search: string) {
    const params = new URLSearchParams(search);
    params.delete('auth');
    params.delete('next');
    const nextSearch = params.toString();
    return `${pathname}${nextSearch ? `?${nextSearch}` : ''}`;
}

function RequireAuth({ children, isLoggedIn }: RequireAuthProps) {
    const location = useLocation();

    if (isLoggedIn) return children;

    return (
        <Navigate
            replace
            state={{ openAuthFor: location.pathname }}
            to="/"
        />
    );
}

function ListingDetailRoute({ isLoggedIn, onBack, onLoginRequired }: { isLoggedIn: boolean; onBack: () => void; onLoginRequired: () => void }) {
    const { listingId } = useParams();
    const parsedListingId = Number(listingId);

    if (!Number.isSafeInteger(parsedListingId) || parsedListingId <= 0) {
        return <Navigate replace to="/" />;
    }

    return (
        <ProductDetail
            isLoggedIn={isLoggedIn}
            listingId={parsedListingId}
            onBack={onBack}
            onLoginRequired={onLoginRequired}
        />
    );
}

function EditListingRoute({ onCancel, onDirtyChange, onSaved }: EditListingRouteProps) {
    const { listingId } = useParams();
    const parsedListingId = Number(listingId);
    const [listing, setListing] = useState<ListingDetail | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [reloadKey, setReloadKey] = useState(0);

    useEffect(() => {
        if (!Number.isSafeInteger(parsedListingId) || parsedListingId <= 0) return;

        let isCurrent = true;
        setListing(null);
        setError(null);

        void fetchListing(parsedListingId)
            .then((result) => isCurrent && setListing(result))
            .catch((reason: unknown) => {
                if (!isCurrent) return;
                setError(reason instanceof Error ? reason.message : '가구 정보를 불러오지 못했습니다.');
            });

        return () => { isCurrent = false; };
    }, [parsedListingId, reloadKey]);

    if (!Number.isSafeInteger(parsedListingId) || parsedListingId <= 0) {
        return <Navigate replace to="/my-listings" />;
    }

    if (error) {
        return (
            <section className={productGridStyles['product-grid-message']}>
                <p>{error}</p>
                <button onClick={() => setReloadKey((current) => current + 1)} type="button">다시 시도</button>
            </section>
        );
    }

    if (!listing) {
        return <section aria-live="polite" className={productGridStyles['product-grid-message']}>가구 정보를 불러오는 중입니다.</section>;
    }

    return (
        <FurnitureRegistration
            key={listing.id}
            listing={listing}
            onCancel={onCancel}
            onDirtyChange={onDirtyChange}
            onSaved={onSaved}
        />
    );
}

function App() {
    const location = useLocation();
    const navigate = useNavigate();
    const navigationType = useNavigationType();
    const locationState = getLocationState(location.state);
    const searchParams = new URLSearchParams(location.search);
    const isAuthModalRequested = searchParams.get('auth') === 'login';
    const listingSearchQuery = searchParams.get('q') ?? '';
    const [isLoggedIn, setIsLoggedIn] = useState(() => Boolean(window.sessionStorage.getItem('setty:auth-token')));
    const isAuthModalOpen = isAuthModalRequested && !isLoggedIn;
    const [isFormDirty, setIsFormDirty] = useState(false);
    const formDirtyRef = useRef(false);
    const homeScrollPositionRef = useRef(0);
    const previousPathnameRef = useRef(location.pathname);
    const focusedPathnameRef = useRef(location.pathname);
    const mainRef = useRef<HTMLElement>(null);
    const { notice: paymentNotice, dismiss: dismissPaymentNotice } = usePaymentReturn(() => {
        navigate('/my-orders', { replace: true });
    });

    const handleDirtyChange = useCallback((isDirty: boolean) => {
        formDirtyRef.current = isDirty;
        setIsFormDirty(isDirty);
    }, []);

    const shouldBlockNavigation = useCallback<BlockerFunction>(({ currentLocation, nextLocation }) => (
        formDirtyRef.current && currentLocation.pathname !== nextLocation.pathname
    ), []);
    const blocker = useBlocker(shouldBlockNavigation);

    useBeforeUnload(useCallback((event) => {
        if (!formDirtyRef.current) return;
        event.preventDefault();
        event.returnValue = '';
    }, []));

    useEffect(() => {
        if (blocker.state !== 'blocked') return;

        if (window.confirm(unsavedChangesMessage)) {
            blocker.proceed();
        } else {
            blocker.reset();
        }
    }, [blocker]);

    useEffect(() => {
        const previousPathname = previousPathnameRef.current;
        previousPathnameRef.current = location.pathname;

        if (location.pathname !== '/' || previousPathname === '/') return;

        const isReturningFromDetail = /^\/listings\/[^/]+$/.test(previousPathname);
        const targetScrollPosition = navigationType === 'POP' && isReturningFromDetail
            ? homeScrollPositionRef.current
            : 0;
        const frame = window.requestAnimationFrame(() => {
            window.scrollTo({ top: targetScrollPosition, behavior: 'auto' });
        });

        return () => window.cancelAnimationFrame(frame);
    }, [location.pathname, navigationType]);

    useEffect(() => {
        document.title = `${getViewTitle(location.pathname)} | SETTY`;
        if (focusedPathnameRef.current === location.pathname) return;

        focusedPathnameRef.current = location.pathname;
        const frame = window.requestAnimationFrame(() => {
            mainRef.current?.focus({ preventScroll: true });
        });

        return () => window.cancelAnimationFrame(frame);
    }, [location.pathname]);

    useEffect(() => {
        if (!locationState.logout) return;

        void logout();
        window.sessionStorage.removeItem('setty:auth-token');
        window.sessionStorage.removeItem('setty:auth-role');
        setIsLoggedIn(false);
        navigate('/', { replace: true, state: null });
    }, [locationState.logout, navigate]);

    useEffect(() => {
        if (!isLoggedIn || !isAuthModalRequested) return;

        navigate(removeAuthSearch(location.pathname, location.search), { replace: true, state: null });
    }, [isAuthModalRequested, isLoggedIn, location.pathname, location.search, navigate]);

    useEffect(() => {
        const requestedPath = locationState.openAuthFor;
        if (!isSafeProtectedPath(requestedPath)) return;

        const params = new URLSearchParams({ auth: 'login', next: requestedPath });
        navigate('/', { replace: true, state: null });
        queueMicrotask(() => {
            navigate(`/?${params.toString()}`, { state: { authModalEntry: true } });
        });
    }, [locationState.openAuthFor, navigate]);

    useEffect(() => {
        if (!isAuthModalOpen || locationState.authModalEntry || locationState.openAuthFor) return;

        const modalUrl = `${location.pathname}${location.search}`;
        const baseUrl = removeAuthSearch(location.pathname, location.search);
        navigate(baseUrl, { replace: true, state: null });
        queueMicrotask(() => {
            navigate(modalUrl, { state: { authModalEntry: true } });
        });
    }, [isAuthModalOpen, location.pathname, location.search, locationState.authModalEntry, locationState.openAuthFor, navigate]);

    const openAuthModal = useCallback((nextPath?: string) => {
        const params = new URLSearchParams(location.search);
        params.set('auth', 'login');
        if (nextPath) {
            params.set('next', nextPath);
        } else {
            params.delete('next');
        }

        navigate(`${location.pathname}?${params.toString()}`, {
            state: { authModalEntry: true }
        });
    }, [location.pathname, location.search, navigate]);

    const closeAuthModal = useCallback(() => {
        if (locationState.authModalEntry) {
            navigate(-1);
            return;
        }

        navigate(removeAuthSearch(location.pathname, location.search), { replace: true, state: null });
    }, [location.pathname, location.search, locationState.authModalEntry, navigate]);

    const finishLogin = useCallback(() => {
        setIsLoggedIn(true);
        const nextPath = searchParams.get('next');

        if (isSafeProtectedPath(nextPath)) {
            navigate(nextPath, { replace: true, state: null });
            return;
        }

        closeAuthModal();
        window.requestAnimationFrame(() => {
            mainRef.current?.focus({ preventScroll: true });
        });
    }, [closeAuthModal, navigate, searchParams]);

    const goHome = useCallback(() => {
        if (location.pathname === '/' && !location.search) {
            window.scrollTo({ top: 0, behavior: 'auto' });
            return;
        }
        navigate('/');
    }, [location.pathname, location.search, navigate]);

    const changeListingSearchQuery = useCallback((query: string) => {
        const normalizedQuery = query.trim();
        const params = new URLSearchParams();
        if (normalizedQuery) {
            params.set('q', normalizedQuery);
        }

        navigate({ pathname: '/', search: params.toString() }, { replace: location.pathname === '/' });
    }, [location.pathname, navigate]);

    const goToAuthenticatedPath = useCallback((path: '/my-listings' | '/my-orders' | '/my-account' | '/my-favorites') => {
        if (isLoggedIn) {
            if (location.pathname === path && !location.search) return;
            navigate(path);
            return;
        }
        openAuthModal(path);
    }, [isLoggedIn, location.pathname, location.search, navigate, openAuthModal]);

    const openDetail = useCallback((listingId: number) => {
        homeScrollPositionRef.current = window.scrollY;
        navigate(`/listings/${listingId}`, { state: { fromHome: true } });
    }, [navigate]);

    const returnFromDetail = useCallback(() => {
        if (locationState.fromHome || locationState.fromMyFavorites) {
            navigate(-1);
            return;
        }
        navigate('/', { replace: true });
    }, [locationState.fromHome, locationState.fromMyFavorites, navigate]);

    const cancelRegistration = useCallback(() => {
        if (locationState.fromMyListings) {
            navigate(-1);
            return;
        }
        navigate('/my-listings', { replace: true });
    }, [locationState.fromMyListings, navigate]);

    const finishRegistration = useCallback(() => {
        if (locationState.fromMyListings) {
            navigate(-1);
            return;
        }
        navigate('/my-listings', { replace: true });
    }, [locationState.fromMyListings, navigate]);

    const viewName = getViewName(location.pathname);

    return (
        <div className={layoutStyles['app-shell']}>
            <div className={layoutStyles['app-shell__content']} aria-hidden={isAuthModalOpen || undefined} inert={isAuthModalOpen}>
                <a className={layoutStyles['skip-link']} href="#main-content">본문으로 건너뛰기</a>
                <Header
                    isLoggedIn={isLoggedIn}
                    onHome={goHome}
                    onLoginClick={() => openAuthModal()}
                    onLogout={() => navigate('/', { replace: true, state: { logout: true } })}
                    onMyAccount={() => goToAuthenticatedPath('/my-account')}
                    onMyListings={() => goToAuthenticatedPath('/my-listings')}
                    onMyOrders={() => goToAuthenticatedPath('/my-orders')}
                    onSearchQueryChange={changeListingSearchQuery}
                    searchQuery={listingSearchQuery}
                />
                {paymentNotice && (
                    <div className={[paymentStyles['payment-notice'], paymentStyles[`payment-notice--${paymentNotice.tone}`]].filter(Boolean).join(' ')} role="status">
                        <span>{paymentNotice.message}</span>
                        <button type="button" onClick={dismissPaymentNotice} aria-label="알림 닫기">✕</button>
                    </div>
                )}
                <main
                    className={[layoutStyles['app-shell__main'], layoutStyles[`app-shell__main--${viewName}`]].filter(Boolean).join(' ')}
                    id="main-content"
                    ref={mainRef}
                    tabIndex={-1}
                >
                    {__ENABLE_MSW__ && location.pathname === '/' && <MockScenarioController />}
                    <Routes>
                        <Route index element={<ProductGrid onProductSelect={openDetail} searchQuery={listingSearchQuery} />} />
                        <Route path="listings/:listingId" element={<ListingDetailRoute isLoggedIn={isLoggedIn} onBack={returnFromDetail} onLoginRequired={() => openAuthModal()} />} />
                        <Route path="my-listings" element={(
                            <RequireAuth isLoggedIn={isLoggedIn}>
                                <MyListings
                                    onEdit={(listingId) => navigate(`/my-listings/${listingId}/edit`, { state: { fromMyListings: true } })}
                                    onRegister={() => navigate('/my-listings/new', { state: { fromMyListings: true } })}
                                />
                            </RequireAuth>
                        )} />
                        <Route path="my-orders" element={(
                            <RequireAuth isLoggedIn={isLoggedIn}>
                                <MyOrders />
                            </RequireAuth>
                        )} />
                        <Route path="my-account" element={(
                            <RequireAuth isLoggedIn={isLoggedIn}>
                                <MyAccount
                                    onFavorites={() => goToAuthenticatedPath('/my-favorites')}
                                    onListings={() => goToAuthenticatedPath('/my-listings')}
                                    onOrders={() => goToAuthenticatedPath('/my-orders')}
                                />
                            </RequireAuth>
                        )} />
                        <Route path="my-favorites" element={(
                            <RequireAuth isLoggedIn={isLoggedIn}>
                                <MyFavorites
                                    onSelect={(listingId) => navigate(`/listings/${listingId}`, { state: { fromMyFavorites: true } })}
                                />
                            </RequireAuth>
                        )} />
                        <Route path="my-listings/new" element={(
                            <RequireAuth isLoggedIn={isLoggedIn}>
                                <FurnitureRegistration
                                    onCancel={cancelRegistration}
                                    onDirtyChange={handleDirtyChange}
                                    onSaved={finishRegistration}
                                />
                            </RequireAuth>
                        )} />
                        <Route path="my-listings/:listingId/edit" element={(
                            <RequireAuth isLoggedIn={isLoggedIn}>
                                <EditListingRoute
                                    onCancel={cancelRegistration}
                                    onDirtyChange={handleDirtyChange}
                                    onSaved={finishRegistration}
                                />
                            </RequireAuth>
                        )} />
                        <Route path="*" element={<Navigate replace to="/" />} />
                    </Routes>
                </main>
            </div>
            {isAuthModalOpen && (
                <AuthModal
                    onClose={closeAuthModal}
                    onLoggedIn={finishLogin}
                />
            )}
            <span aria-live="polite" className={layoutStyles['site-header__visually-hidden']}>
                {isFormDirty ? '작성 중인 가구 정보가 있습니다.' : ''}
            </span>
        </div>
    );
}

export default App;
