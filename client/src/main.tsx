import { createRoot } from 'react-dom/client';
import { RouterProvider } from 'react-router/dom';
import { router } from './App';
import './styles/global.css';

async function enableMocking() {
    if (!__ENABLE_MSW__) return;

    const { worker } = await import('./mocks/browser');
    await worker.start({ onUnhandledRequest: 'bypass' });
}

async function bootstrap() {
    await enableMocking();

    const root = document.getElementById('root');
    if (!root) throw new Error('Root 요소를 찾을 수 없습니다.');

    createRoot(root).render(<RouterProvider router={router} />);
}

void bootstrap();
