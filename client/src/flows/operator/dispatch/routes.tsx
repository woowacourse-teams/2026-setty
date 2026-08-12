import { type RouteObject } from 'react-router-dom';
import DispatchRequestDetailPage from './pages/DispatchRequestDetailPage';
import DispatchRequestListPage from './pages/DispatchRequestListPage';

export const operatorDispatchRoutes: RouteObject[] = [
  {
    path: 'dispatch-requests',
    element: <DispatchRequestListPage />,
  },
  {
    path: 'dispatch-requests/:id',
    element: <DispatchRequestDetailPage />,
  },
];
