import { type RouteObject, useParams } from 'react-router-dom';
import HomePage from './pages/HomePage';
import InboxPage from './pages/InboxPage';
import ListingDetailPage from './pages/ListingDetailPage';
import ListingFormPage from './pages/ListingFormPage';
import MessageComposePage from './pages/MessageComposePage';
import MinePage from './pages/MinePage';

function ListingDetailRoute() {
  const { listingId } = useParams();
  return <ListingDetailPage key={listingId} />;
}

function MessageComposeRoute() {
  const { listingId } = useParams();
  return <MessageComposePage key={listingId} />;
}

function ListingEditRoute() {
  const { listingId } = useParams();
  return <ListingFormPage key={listingId} />;
}

export const marketplaceRoutes: RouteObject[] = [
  {
    path: '/',
    element: <HomePage />,
  },
  {
    path: '/listings/:listingId',
    element: <ListingDetailRoute />,
  },
  {
    path: '/listings/:listingId/message',
    element: <MessageComposeRoute />,
  },
  {
    path: '/inbox',
    element: <InboxPage />,
  },
  {
    path: '/mine',
    element: <MinePage />,
  },
  {
    path: '/mine/new',
    element: <ListingFormPage />,
  },
  {
    path: '/mine/:listingId/edit',
    element: <ListingEditRoute />,
  },
];
