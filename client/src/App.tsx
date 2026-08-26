import { Header } from './components/Header';
import { ProductDetail } from './components/ProductDetail';
import './styles/global.css';

function App() {
    return (
        <div className="app-shell">
            <Header />
            <main className="app-shell__main">
                <ProductDetail />
            </main>
        </div>
    );
}

export default App;
