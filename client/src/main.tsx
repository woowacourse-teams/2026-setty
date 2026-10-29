import { createRoot } from 'react-dom/client';
import { RouterProvider } from 'react-router/dom';
import { router } from './App';
import { initializeAnalytics } from './analytics/posthog';
import './styles/global.css';

function bootstrap() {
    initializeAnalytics();

    const root = document.getElementById('root');
    if (!root) throw new Error('Root 요소를 찾을 수 없습니다.');

    createRoot(root).render(<RouterProvider router={router} />);
}

bootstrap();
