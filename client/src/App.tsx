import { useState } from 'react';
import { fetchListing, type ListingDetail } from './api/listings';
import { AuthModal } from './components/AuthModal';
import { FurnitureRegistration } from './components/FurnitureRegistration';
import { Header } from './components/Header';
import { MockScenarioController } from './components/MockScenarioController';
import { MyListings } from './components/MyListings';
import { MyOrders } from './components/MyOrders';
import { ProductDetail } from './components/ProductDetail';
import { ProductGrid } from './components/ProductGrid';
import './styles/global.css';

type View = 'home' | 'detail' | 'my-listings' | 'my-orders' | 'registration';

function App() {
    const [view, setView] = useState<View>('home');
    const [selectedListingId, setSelectedListingId] = useState<number | null>(null);
    const [editingListing, setEditingListing] = useState<ListingDetail | undefined>();
    const [isAuthModalOpen, setIsAuthModalOpen] = useState(false);
    const [viewAfterLogin, setViewAfterLogin] = useState<View | null>(null);
    const [isLoggedIn, setIsLoggedIn] = useState(() => Boolean(window.sessionStorage.getItem('setty:auth-token')));

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
        setEditingListing(undefined);
        setView('home');
    };

    return (
        <div className="app-shell">
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
            />
            <main className={`app-shell__main ${view === 'registration' ? 'app-shell__main--registration' : ''}`}>
                {__ENABLE_MSW__ && view === 'home' && <MockScenarioController />}
                {view === 'home' && <ProductGrid onProductSelect={(listingId) => { setSelectedListingId(listingId); setView('detail'); }} />}
                {view === 'detail' && selectedListingId && <ProductDetail listingId={selectedListingId} onBack={goHome} onLoginRequired={() => {
                    setViewAfterLogin('detail');
                    setIsAuthModalOpen(true);
                }} />}
                {view === 'my-listings' && <MyListings onEdit={(listingId) => void openEdit(listingId)} onRegister={() => { setEditingListing(undefined); goToAuthenticatedView('registration'); }} />}
                {view === 'my-orders' && <MyOrders />}
                {view === 'registration' && <FurnitureRegistration key={editingListing?.id ?? 'new'} listing={editingListing} onCancel={() => setView('my-listings')} onSaved={() => { setEditingListing(undefined); setView('my-listings'); }} />}
            </main>
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
