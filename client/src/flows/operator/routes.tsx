import { Navigate, type RouteObject } from 'react-router-dom';
import OperatorLoginPage from '@/flows/operator/auth/OperatorLoginPage';
import OperatorProtectedRoute from '@/flows/operator/auth/OperatorProtectedRoute';
import { operatorDispatchRoutes } from '@/flows/operator/dispatch/routes';
import { operatorEstimateRoutes } from '@/flows/operator/estimate/routes';
import OperatorShell from '@/flows/operator/shell/OperatorShell';

export const operatorRoutes: RouteObject[] = [
  {
    path: '/operator/login',
    element: <OperatorLoginPage />,
  },
  {
    element: <OperatorProtectedRoute />,
    children: [
      {
        path: '/operator',
        element: <OperatorShell />,
        children: [
          {
            index: true,
            element: <Navigate replace to="estimate-requests" />,
          },
          ...operatorEstimateRoutes,
          ...operatorDispatchRoutes,
        ],
      },
    ],
  },
];
