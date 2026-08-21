type AnalyticsEventParameter = string | number | boolean;

type AnalyticsEventParameters = Record<string, AnalyticsEventParameter>;

type Gtag = (command: string, ...args: unknown[]) => void;

declare global {
  interface Window {
    dataLayer?: unknown[];
    gtag?: Gtag;
  }
}

const SCRIPT_ID = 'setty-google-analytics';
const MEASUREMENT_ID_PATTERN = /^G-[A-Z0-9]+$/;

let initializedMeasurementId: string | null = null;
let lastPagePath: string | null = null;

function getMeasurementId() {
  const measurementId = process.env.SETTY_GA_MEASUREMENT_ID?.trim().toUpperCase();
  return measurementId && MEASUREMENT_ID_PATTERN.test(measurementId)
    ? measurementId
    : null;
}

function createGtag(): Gtag {
  return function gtag() {
    // Google 태그 명령 큐는 호출 당시의 Arguments 객체를 그대로 받는다.
    // eslint-disable-next-line prefer-rest-params
    window.dataLayer?.push(arguments);
  };
}

export function initializeGoogleAnalytics() {
  const measurementId = getMeasurementId();
  if (!measurementId) return false;
  if (initializedMeasurementId === measurementId && window.gtag) return true;

  window.dataLayer ??= [];
  window.gtag ??= createGtag();

  if (!document.getElementById(SCRIPT_ID)) {
    const script = document.createElement('script');
    script.async = true;
    script.id = SCRIPT_ID;
    script.src = `https://www.googletagmanager.com/gtag/js?id=${encodeURIComponent(
      measurementId,
    )}`;
    document.head.append(script);
  }

  window.gtag('js', new Date());
  window.gtag('config', measurementId, { send_page_view: false });
  initializedMeasurementId = measurementId;
  return true;
}

function trackEvent(name: string, parameters: AnalyticsEventParameters = {}) {
  if (!initializeGoogleAnalytics() || !window.gtag) return;
  window.gtag('event', name, parameters);
}

export function trackPageView(pagePath: string) {
  if (pagePath === lastPagePath) return;
  if (!initializeGoogleAnalytics() || !window.gtag) return;

  lastPagePath = pagePath;
  window.gtag('event', 'page_view', {
    page_location: `${window.location.origin}${pagePath}`,
    page_path: pagePath,
  });
}

export type ListingDetailSource = 'card_tap' | 'detail_button' | 'swipe_right';

export function trackListingDetailOpened(
  listingId: number,
  source: ListingDetailSource,
) {
  trackEvent('listing_detail_opened', {
    listing_id: listingId,
    source,
  });
}

export function trackMessageSent(listingId: number) {
  trackEvent('message_sent', { listing_id: listingId });
}
