import { Header } from './components/Header';
import './styles/global.css';

function App() {
    return (
        <div className="app-shell">
            <Header />
            <main className="page-content" />
        </div>
    );
}

export default App;
