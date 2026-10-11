import { browser } from 'k6/browser';
import { check } from 'k6';
import exec from 'k6/execution';
import { Counter } from 'k6/metrics';
import { CHECKOUT_SELECTOR, closeCheckout } from '../checkout.js';
import { postHogInitScript, postHogEventCount, waitForPostHogEvent } from '../posthog.js';

const APP_URL = __ENV.FIXTURE_APP_URL;
if (!/^http:\/\/127\.0\.0\.1:\d+$/.test(APP_URL || '')) {
    throw new Error('결제창 닫기 회귀 테스트는 loopback fixture에서만 실행할 수 있습니다.');
}
const cases = ['normal', 'missed_click', 'delayed', 'paused_raf', 'stuck'];
const completedCases = new Counter('checkout_close_cases');

export const options = {
    scenarios: {
        checkout_close_regression: {
            executor: 'per-vu-iterations', vus: 1, iterations: cases.length, maxDuration: '1m',
            options: { browser: { type: 'chromium' } }
        }
    },
    thresholds: { checks: ['rate==1.0'], checkout_close_cases: ['count==5'] }
};

export default async function () {
    const name = cases[exec.scenario.iterationInTest];
    const loginId = `fixture-${name}`;
    const page = await browser.newPage();
    let actualError = null;
    try {
        await page.context().addInitScript(postHogInitScript(APP_URL));
        await page.goto(`${APP_URL}/?case=${name}`, { waitUntil: 'domcontentloaded' });
        await page.locator(CHECKOUT_SELECTOR).waitFor({ state: 'visible' });
        try {
            await closeCheckout(page, { loginId, listingId: 501 });
        } catch (error) {
            actualError = error;
        }
        const state = await page.evaluate((selector) => {
            return { present: Boolean(document.querySelector(selector)), clicks: window.fixtureClicks };
        }, CHECKOUT_SELECTOR);
        if (name === 'stuck') {
            check(actualError, { '닫히지 않는 모달은 성공으로 처리하지 않음': (error) => Boolean(error && error.message.includes(loginId) && error.message.includes('listingId=501') && error.message.includes('modalPresent=true')) });
            check(state, { '닫기 시도를 제한하고 열린 모달을 진단': (value) => value.present && value.clicks === 3 });
            const events = await postHogEventCount(page, 'checkout_closed', loginId, 501);
            check(events, { '닫기 실패에서 이탈 이벤트를 만들지 않음': (value) => value === 0 });
        } else {
            check(actualError, { [`${name}: 실제 모달 제거를 확인`]: (error) => error === null && !state.present });
            check(state.clicks, { [`${name}: 필요한 경우에만 재클릭`]: (clicks) => clicks === (name === 'missed_click' ? 2 : 1) });
            await waitForPostHogEvent(page, 'checkout_closed', loginId, 0, 501, 3000);
            const events = await postHogEventCount(page, 'checkout_closed', loginId, 501);
            check(events, { [`${name}: 실제 이탈 이벤트 HTTP 2xx를 한 번 확인`]: (value) => value === 1 });
        }
        completedCases.add(1);
    } finally {
        await page.close();
    }
}
