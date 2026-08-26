import { Header } from './components/Header';
import { ProductListing } from './components/ProductListing';
import './styles/global.css';

function App() {
    return (
        <div className="app-shell">
            <Header />
            <main className="app-shell__main">
                <ProductListing />
            </main>
        </div>
    );
}

export default App;
