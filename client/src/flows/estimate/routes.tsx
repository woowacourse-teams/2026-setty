import { type RouteObject } from 'react-router-dom';
import EstimateRequestPage from '@/flows/estimate/pages/EstimateRequestPage';
import EstimateSubmittedPage from '@/flows/estimate/pages/EstimateSubmittedPage';
import EstimatePrivacyPolicyPage from '@/flows/estimate/privacy/EstimatePrivacyPolicyPage';

export const estimateRoutes: RouteObject[] = [
  {
    path: '/estimate',
    element: <EstimateRequestPage />,
  },
  {
    path: '/estimate/privacy',
    element: <EstimatePrivacyPolicyPage />,
  },
  {
    path: '/estimate/submitted',
    element: <EstimateSubmittedPage />,
  },
];
