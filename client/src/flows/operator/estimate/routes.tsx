import { type RouteObject } from 'react-router-dom';
import EstimateRequestDetailPage from './pages/EstimateRequestDetailPage';
import EstimateRequestListPage from './pages/EstimateRequestListPage';

export const operatorEstimateRoutes: RouteObject[] = [
  {
    path: 'estimate-requests',
    element: <EstimateRequestListPage />,
  },
  {
    path: 'estimate-requests/:id',
    element: <EstimateRequestDetailPage />,
  },
];
