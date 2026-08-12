import { type RouteObject, useRoutes } from 'react-router-dom';
import { estimateRoutes } from '@/flows/estimate/routes';
import { operatorRoutes } from '@/flows/operator/routes';
import NotFoundPage from '@/app/not-found/NotFoundPage';
import { dispatchRoutes } from './dispatchRoutes';
import { onboardingRoutes } from './onboardingRoutes';

const appRoutes: RouteObject[] = [
  ...onboardingRoutes,
  ...dispatchRoutes,
  ...estimateRoutes,
  ...operatorRoutes,
  {
    path: '*',
    element: <NotFoundPage />,
  },
];

export default function AppRoutes() {
  return useRoutes(appRoutes);
}
