import { Header } from './components/Header';
import { MockScenarioController } from './components/MockScenarioController';
import { ProductGrid } from './components/ProductGrid';
import './styles/global.css';

function App() {
    return (
        <div className="app-shell">
            <Header />
            <main className="app-shell__main">
                {__ENABLE_MSW__ && <MockScenarioController />}
                <ProductGrid />
            </main>
        </div>
    );
}

export default App;
