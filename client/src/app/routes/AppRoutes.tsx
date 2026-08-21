import { type RouteObject, useRoutes } from 'react-router-dom';
import NotFoundPage from '@/app/not-found/NotFoundPage';
import { marketplaceRoutes } from '@/flows/marketplace/routes';

const appRoutes: RouteObject[] = [
  ...marketplaceRoutes,
  {
    path: '*',
    element: <NotFoundPage />,
  },
];

export default function AppRoutes() {
  return useRoutes(appRoutes);
}
