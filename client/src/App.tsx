import { useState } from 'react';
import { Header } from './components/Header';
import { ProductDetail } from './components/ProductDetail';
import { ProductGrid } from './components/ProductGrid';
import './styles/global.css';

function App() {
    const [isDetailOpen, setIsDetailOpen] = useState(false);

    return (
        <div className="app-shell">
            <Header onHome={() => setIsDetailOpen(false)} />
            <main className="app-shell__main">
                {isDetailOpen ? <ProductDetail /> : <ProductGrid onProductSelect={() => setIsDetailOpen(true)} />}
            </main>
        </div>
    );
}

export default App;
