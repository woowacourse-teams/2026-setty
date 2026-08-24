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

  test('이벤트에는 매물 식별자와 상세 진입 방식만 기록한다', async () => {
    process.env.SETTY_GA_MEASUREMENT_ID = 'G-TEST123456';
    const { trackListingDetailOpened, trackMessageSent } = await loadAnalytics();

    trackListingDetailOpened(11, 'swipe_right');
    trackMessageSent(11);

    const queuedCommands = window.dataLayer?.map((command) => Array.from(command as IArguments));
    expect(queuedCommands).toEqual(
      expect.arrayContaining([
        [
          'event',
          'listing_detail_opened',
          { listing_id: 11, detail_open_method: 'swipe_right' },
        ],
        ['event', 'message_sent', { listing_id: 11 }],
      ]),
    );
  });
});
