import { useState } from 'react';
import { AuthModal } from './components/AuthModal';
import { Header } from './components/Header';
import { ProductGrid } from './components/ProductGrid';
import './styles/global.css';

function App() {
    const [isAuthModalOpen, setIsAuthModalOpen] = useState(false);
    return (
        <div className="app-shell">
            <Header onLoginClick={() => setIsAuthModalOpen(true)} />
            <main className="app-shell__main">
                <ProductGrid />
            </main>
            {isAuthModalOpen && <AuthModal onClose={() => setIsAuthModalOpen(false)} />}
        </div>
    );
}

export default App;
