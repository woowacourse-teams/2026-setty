import { useState } from 'react';
import { AuthModal } from './components/AuthModal';
import { Header } from './components/Header';
import { MockScenarioController } from './components/MockScenarioController';
import { ProductDetail } from './components/ProductDetail';
import { ProductGrid } from './components/ProductGrid';
import './styles/global.css';

function App() {
    const [isDetailOpen, setIsDetailOpen] = useState(false);
    const [isAuthModalOpen, setIsAuthModalOpen] = useState(false);

    return (
        <div className="app-shell">
            <Header onHome={() => setIsDetailOpen(false)} onLoginClick={() => setIsAuthModalOpen(true)} />
            <main className="app-shell__main">
                {__ENABLE_MSW__ && <MockScenarioController />}
                {isDetailOpen ? <ProductDetail /> : <ProductGrid onProductSelect={() => setIsDetailOpen(true)} />}
            </main>
            {isAuthModalOpen && <AuthModal onClose={() => setIsAuthModalOpen(false)} />}
        </div>
    );
}

export default App;
