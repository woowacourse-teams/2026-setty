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

export type ListingDetailOpenMethod = 'card_tap' | 'detail_button' | 'swipe_right';
export type ListingSkipMethod = 'skip_button' | 'swipe_left';
export type ListingPriceBucket =
  | 'unavailable'
  | 'zero'
  | 'under_50k'
  | '50k_to_100k'
  | '100k_to_300k'
  | 'over_300k';

export function getListingPriceBucket(price?: number | null): ListingPriceBucket {
  if (price === null || price === undefined || !Number.isFinite(price) || price < 0) {
    return 'unavailable';
  }
  if (price === 0) return 'zero';
  if (price < 50_000) return 'under_50k';
  if (price < 100_000) return '50k_to_100k';
  if (price < 300_000) return '100k_to_300k';
  return 'over_300k';
}

function listingParameters(listingId: number, price?: number | null) {
  return {
    listing_id: listingId,
    price_bucket: getListingPriceBucket(price),
  };
}

export function trackListingCardImpression(listingId: number, price?: number | null) {
  trackEvent('listing_card_impression', listingParameters(listingId, price));
}

export function trackListingSkipped(
  listingId: number,
  price: number | null | undefined,
  skipMethod: ListingSkipMethod,
) {
  trackEvent('listing_skipped', {
    ...listingParameters(listingId, price),
    skip_method: skipMethod,
  });
}

export function trackListingDetailOpened(
  listingId: number,
  price: number | null | undefined,
  detailOpenMethod: ListingDetailOpenMethod,
) {
  trackEvent('listing_detail_opened', {
    ...listingParameters(listingId, price),
    detail_open_method: detailOpenMethod,
  });
}

export function trackMessageComposeOpened(listingId: number, price?: number | null) {
  trackEvent('message_compose_opened', listingParameters(listingId, price));
}

export function trackMessageSent(listingId: number, price?: number | null) {
  trackEvent('message_sent', listingParameters(listingId, price));
}

export function trackListingCreateStarted() {
  trackEvent('listing_create_started');
}

export function trackListingCreated(listingId: number, price: number) {
  trackEvent('listing_created', listingParameters(listingId, price));
}
