import { useState } from 'react';
import { AuthModal } from './components/AuthModal';
import { Header } from './components/Header';
import { MockScenarioController } from './components/MockScenarioController';
import { ProductGrid } from './components/ProductGrid';
import './styles/global.css';

function App() {
    const [isAuthModalOpen, setIsAuthModalOpen] = useState(false);
    return (
        <div className="app-shell">
            <Header onLoginClick={() => setIsAuthModalOpen(true)} />
            <main className="app-shell__main">
                {__ENABLE_MSW__ && <MockScenarioController />}
                <ProductGrid />
            </main>
            {isAuthModalOpen && <AuthModal onClose={() => setIsAuthModalOpen(false)} />}
        </div>
    );
}

export default App;
