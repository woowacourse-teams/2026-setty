import { browser } from 'k6/browser';
import { check } from 'k6';
import exec from 'k6/execution';
import http from 'k6/http';
import { PAYMENT_CALLBACK_ROUTE, routePaymentCallbacks, waitForPaymentResult, waitForConfirmedOrder, waitForAppReturn } from '../payment.js';
import { postHogInitScript, waitForPostHogEvent } from '../posthog.js';

// Local fixtures only: no real credentials, Toss requests, or Setty data.
const APP_URL = __ENV.FIXTURE_APP_URL;
const TOSS_URL = __ENV.FIXTURE_TOSS_URL;
if (![APP_URL, TOSS_URL].every((url) => /^http:\/\/127\.0\.0\.1:\d+$/.test(url || ''))) {
    throw new Error('결제 복귀 회귀 테스트는 loopback fixture에서만 실행할 수 있습니다.');
}
const cases = [
    { name: 'success' },
    { name: 'declined', error: /PAYMENT_CONFIRM_FAILED/ },
    { name: 'cancelled', error: /PAY_PROCESS_CANCELED/ },
    { name: 'html', error: /결제 승인 API 확인 실패/ },
    { name: 'wrong_order', error: /결제 승인 API 확인 실패/ },
    { name: 'pending', error: /DONE이지만 주문 확정/ },
    { name: 'expired', error: /status=EXPIRED/ }
];

export const options = {
    scenarios: {
        payment_regression: {
            executor: 'per-vu-iterations', vus: 1, iterations: cases.length, maxDuration: '2m',
            options: { browser: { type: 'chromium' } }
        }
    },
    thresholds: { checks: ['rate==1.0'] }
};

export default async function () {
    const testCase = cases[exec.scenario.iterationInTest];
    const page = await browser.newPage();
    let payment;
    let actualError = null;
    try {
        await page.context().addInitScript(postHogInitScript(APP_URL));
        await page.goto(`${APP_URL}/setup?case=${testCase.name}`, { waitUntil: 'domcontentloaded' });
        if (testCase.name === 'success') {
            const response = http.get(`${APP_URL}/dev/payments/success`);
            check(response, { 'dev 복귀가 SPA HTML인 기존 장애를 재현': (value) => value.status === 200 && value.body.includes('<html') });
        }
        payment = await routePaymentCallbacks(page, { appUrl: APP_URL, appOrigin: APP_URL, apiBaseUrl: `${APP_URL}/dev` });
        await page.goto(`${TOSS_URL}/?case=${testCase.name}`, { waitUntil: 'domcontentloaded' });
        // Cross-origin navigation back from an authentication page, as in Toss.
        // Simulate the customer's action inside the authentication document;
        // k6 locators themselves can lose their isolated world across origins.
        await page.evaluate(() => {
            const href = document.querySelector('a').href;
            setTimeout(() => window.location.assign(href), 0);
        });
        const orderId = await waitForPaymentResult(page, payment, 5000);
        await page.unroute(PAYMENT_CALLBACK_ROUTE);
        check(orderId, { '토스 복귀 주문을 실제 승인 응답과 대조': (value) => value === 101 + exec.scenario.iterationInTest });
        await waitForConfirmedOrder(page, payment, 501 + exec.scenario.iterationInTest, 1500);
        await waitForAppReturn(page, APP_URL, 5000);
        // The SPA removes its query immediately and has no success notice. Verify
        // the normal app event still arrives, without relying on a DOM locator.
        await waitForPostHogEvent(page, 'payment_succeeded', 'fixture-buyer', 0, undefined, 5000);
    } catch (error) {
        actualError = error;
    } finally {
        if (payment) await page.unroute(PAYMENT_CALLBACK_ROUTE);
        await page.close();
    }
    check(actualError, {
        [`${testCase.name}: 승인·주문 결과를 올바르게 판정`]: (error) => testCase.error
            ? Boolean(error && testCase.error.test(error.message)) : error === null
    });
    if (actualError && (!testCase.error || !testCase.error.test(actualError.message))) {
        throw actualError;
    }
}
