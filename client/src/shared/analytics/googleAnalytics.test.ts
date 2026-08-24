import { afterEach, beforeEach, describe, expect, jest, test } from '@jest/globals';

const ORIGINAL_MEASUREMENT_ID = process.env.SETTY_GA_MEASUREMENT_ID;

async function loadAnalytics() {
  return import('./googleAnalytics');
}

beforeEach(() => {
  jest.resetModules();
  document.getElementById('setty-google-analytics')?.remove();
  delete window.dataLayer;
  delete window.gtag;
});

afterEach(() => {
  process.env.SETTY_GA_MEASUREMENT_ID = ORIGINAL_MEASUREMENT_ID;
});

describe('Google Analytics', () => {
  test('측정 ID가 없으면 스크립트와 이벤트를 만들지 않는다', async () => {
    delete process.env.SETTY_GA_MEASUREMENT_ID;
    const { trackPageView } = await loadAnalytics();

    trackPageView('/');

    expect(document.getElementById('setty-google-analytics')).not.toBeInTheDocument();
    expect(window.dataLayer).toBeUndefined();
  });

  test('측정 ID가 있으면 태그를 한 번만 초기화하고 페이지 조회를 기록한다', async () => {
    process.env.SETTY_GA_MEASUREMENT_ID = 'G-TEST123456';
    const { trackPageView } = await loadAnalytics();

    trackPageView('/');
    trackPageView('/');

    const script = document.getElementById('setty-google-analytics');
    expect(script).toHaveAttribute(
      'src',
      'https://www.googletagmanager.com/gtag/js?id=G-TEST123456',
    );
    expect(window.dataLayer).toHaveLength(3);
  });

  test('가격을 원문 대신 분석 구간으로 변환한다', async () => {
    const { getListingPriceBucket } = await loadAnalytics();

    expect(getListingPriceBucket(undefined)).toBe('unavailable');
    expect(getListingPriceBucket(null)).toBe('unavailable');
    expect(getListingPriceBucket(-1)).toBe('unavailable');
    expect(getListingPriceBucket(0)).toBe('zero');
    expect(getListingPriceBucket(49_999)).toBe('under_50k');
    expect(getListingPriceBucket(50_000)).toBe('50k_to_100k');
    expect(getListingPriceBucket(99_999)).toBe('50k_to_100k');
    expect(getListingPriceBucket(100_000)).toBe('100k_to_300k');
    expect(getListingPriceBucket(299_999)).toBe('100k_to_300k');
    expect(getListingPriceBucket(300_000)).toBe('over_300k');
  });

  test('행동 이벤트에는 식별자, 가격 구간과 행동 방식만 기록한다', async () => {
    process.env.SETTY_GA_MEASUREMENT_ID = 'G-TEST123456';
    const {
      trackListingCardImpression,
      trackListingCreated,
      trackListingCreateStarted,
      trackListingDetailOpened,
      trackListingSkipped,
      trackMessageComposeOpened,
      trackMessageSent,
    } = await loadAnalytics();

    trackListingCardImpression(11, 55_000);
    trackListingSkipped(11, null, 'swipe_left');
    trackListingDetailOpened(11, 100_000, 'swipe_right');
    trackMessageComposeOpened(11, 0);
    trackMessageSent(11, 299_999);
    trackListingCreateStarted();
    trackListingCreated(12, 300_000);

    const queuedCommands = window.dataLayer?.map((command) => Array.from(command as IArguments));
    expect(queuedCommands).toEqual(
      expect.arrayContaining([
        [
          'event',
          'listing_card_impression',
          { listing_id: 11, price_bucket: '50k_to_100k' },
        ],
        [
          'event',
          'listing_skipped',
          {
            listing_id: 11,
            price_bucket: 'unavailable',
            skip_method: 'swipe_left',
          },
        ],
        [
          'event',
          'listing_detail_opened',
          {
            listing_id: 11,
            price_bucket: '100k_to_300k',
            detail_open_method: 'swipe_right',
          },
        ],
        [
          'event',
          'message_compose_opened',
          { listing_id: 11, price_bucket: 'zero' },
        ],
        [
          'event',
          'message_sent',
          { listing_id: 11, price_bucket: '100k_to_300k' },
        ],
        ['event', 'listing_create_started', {}],
        [
          'event',
          'listing_created',
          { listing_id: 12, price_bucket: 'over_300k' },
        ],
      ]),
    );
  });
});
