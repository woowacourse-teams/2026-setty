import { Header } from './components/Header';
import { ProductGrid } from './components/ProductGrid';
import './styles/global.css';

function App() {
    return (
        <div className="app-shell">
            <Header />
            <main className="app-shell__main">
                <ProductGrid />
            </main>
        </div>
    );
}

export default App;
