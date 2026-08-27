import { Header } from './components/Header';
import { FurnitureRegistration } from './components/FurnitureRegistration';
import './styles/global.css';

function App() {
    return (
        <div className="app-shell">
            <Header onHome={() => undefined} />
            <main className="app-shell__main app-shell__main--registration">
                <FurnitureRegistration />
            </main>
        </div>
    );
}

export default App;
