import { useEffect, useRef, useState } from 'react';
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
import './styles/global.css';

type View = 'home' | 'detail' | 'my-listings' | 'my-orders' | 'registration' | 'my-account' | 'my-favorites';

function App() {
    const [view, setView] = useState<View>('home');
    const [selectedListingId, setSelectedListingId] = useState<number | null>(null);
    const [editingListing, setEditingListing] = useState<ListingDetail | undefined>();
    const [isAuthModalOpen, setIsAuthModalOpen] = useState(false);
    const [viewAfterLogin, setViewAfterLogin] = useState<View | null>(null);
    const [isLoggedIn, setIsLoggedIn] = useState(() => Boolean(window.sessionStorage.getItem('setty:auth-token')));
    const homeScrollPositionRef = useRef(0);
    const shouldRestoreHomeScrollRef = useRef(false);
    const { notice: paymentNotice, dismiss: dismissPaymentNotice } = usePaymentReturn(() => setView('my-orders'));

    useEffect(() => {
        const shouldRestoreHomeScroll = view === 'home' && shouldRestoreHomeScrollRef.current;
        const targetScrollPosition = shouldRestoreHomeScroll ? homeScrollPositionRef.current : 0;
        shouldRestoreHomeScrollRef.current = false;

        const frame = window.requestAnimationFrame(() => {
            window.scrollTo({ top: targetScrollPosition, behavior: 'auto' });
        });

        return () => window.cancelAnimationFrame(frame);
    }, [view]);

    const goToAuthenticatedView = (nextView: View) => {
        if (isLoggedIn) {
            setView(nextView);
            return;
        }
        setViewAfterLogin(nextView);
        setIsAuthModalOpen(true);
    };

    const openEdit = async (listingId: number) => {
        try {
            const listing = await fetchListing(listingId);
            setEditingListing(listing);
            setView('registration');
        } catch {
            setView('my-listings');
        }
    };

    const goHome = () => {
        shouldRestoreHomeScrollRef.current = false;
        setEditingListing(undefined);
        setView('home');

        if (view === 'home') {
            window.scrollTo({ top: 0, behavior: 'auto' });
        }
    };

    const returnToHome = () => {
        shouldRestoreHomeScrollRef.current = true;
        setEditingListing(undefined);
        setView('home');
    };

    const openDetail = (listingId: number) => {
        homeScrollPositionRef.current = window.scrollY;
        setSelectedListingId(listingId);
        setView('detail');
    };

    return (
        <div className="app-shell">
            <div className="app-shell__content" aria-hidden={isAuthModalOpen || undefined} inert={isAuthModalOpen}>
                <Header
                    isLoggedIn={isLoggedIn}
                    onHome={goHome}
                    onLoginClick={() => setIsAuthModalOpen(true)}
                    onLogout={() => {
                        window.sessionStorage.removeItem('setty:auth-token');
                        window.sessionStorage.removeItem('setty:auth-role');
                        setIsLoggedIn(false);
                        goHome();
                    }}
                    onMyListings={() => goToAuthenticatedView('my-listings')}
                    onMyOrders={() => goToAuthenticatedView('my-orders')}
                    onMyAccount={() => goToAuthenticatedView('my-account')}
                />
                {paymentNotice && (
                    <div className={`payment-notice payment-notice--${paymentNotice.tone}`} role="status">
                        <span>{paymentNotice.message}</span>
                        <button type="button" onClick={dismissPaymentNotice} aria-label="알림 닫기">✕</button>
                    </div>
                )}
                <main className={`app-shell__main app-shell__main--${view}`}>
                    {__ENABLE_MSW__ && view === 'home' && <MockScenarioController />}
                    {view === 'home' && <ProductGrid onProductSelect={openDetail} />}
                    {view === 'detail' && selectedListingId && <ProductDetail listingId={selectedListingId} isLoggedIn={isLoggedIn} onBack={returnToHome} onLoginRequired={() => {
                        setViewAfterLogin('detail');
                        setIsAuthModalOpen(true);
                    }} />}
                    {view === 'my-listings' && <MyListings onEdit={(listingId) => void openEdit(listingId)} onRegister={() => { setEditingListing(undefined); goToAuthenticatedView('registration'); }} />}
                    {view === 'my-orders' && <MyOrders />}
                    {view === 'my-account' && <MyAccount
                        onFavorites={() => goToAuthenticatedView('my-favorites')}
                        onOrders={() => goToAuthenticatedView('my-orders')}
                        onListings={() => goToAuthenticatedView('my-listings')}
                    />}
                    {view === 'my-favorites' && <MyFavorites onSelect={(listingId) => {
                        setSelectedListingId(listingId);
                        setView('detail');
                    }} />}
                    {view === 'registration' && <FurnitureRegistration key={editingListing?.id ?? 'new'} listing={editingListing} onCancel={() => setView('my-listings')} onSaved={() => { setEditingListing(undefined); setView('my-listings'); }} />}
                </main>
            </div>
            {isAuthModalOpen && <AuthModal onClose={() => setIsAuthModalOpen(false)} onLoggedIn={() => {
                setIsLoggedIn(true);
                setIsAuthModalOpen(false);
                setView(viewAfterLogin ?? 'home');
                setViewAfterLogin(null);
            }} />}
        </div>
    );
}

export default App;
