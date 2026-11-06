import { createRoot } from 'react-dom/client';
import { RouterProvider } from 'react-router/dom';
import { router } from './App';
import { initializeAnalytics } from './analytics/posthog';
import { listenSimulatorLinks } from './features/driver/simulatorLink';
import { listenForegroundMessages, listenNotificationClicks, syncPushToken } from './push/webPush';
import './styles/global.css';

function bootstrap() {
    listenSimulatorLinks();
    initializeAnalytics();

    const root = document.getElementById('root');
    if (!root) throw new Error('Root 요소를 찾을 수 없습니다.');

    createRoot(root).render(<RouterProvider router={router} />);
    void syncPushToken();
    void listenForegroundMessages((path) => void router.navigate(path));
    listenNotificationClicks((path) => void router.navigate(path));
}

bootstrap();
