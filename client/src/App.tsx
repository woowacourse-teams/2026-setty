import { Header } from './components/Header';
import './styles/global.css';

function App() {
    return (
        <div className="app-shell">
            <Header />
            <main className="app-shell__main" />
        </div>
    );
}

export default App;
