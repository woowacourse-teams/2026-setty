import { useEffect } from 'react';
import { useLocation } from 'react-router-dom';
import { trackPageView } from './googleAnalytics';

export default function GoogleAnalyticsTracker() {
  const { pathname, search } = useLocation();

  useEffect(() => {
    trackPageView(`${pathname}${search}`);
  }, [pathname, search]);

  return null;
}
