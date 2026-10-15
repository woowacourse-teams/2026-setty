import { browser } from 'k6/browser';
import { check } from 'k6';
import { SharedArray } from 'k6/data';
import exec from 'k6/execution';
import { Counter } from 'k6/metrics';
import { CHECKOUT_SELECTOR, closeCheckout } from './checkout.js';
import { postHogInitScript, postHogEventCount, waitForPostHogEvent } from './posthog.js';
import { PAYMENT_CALLBACK_ROUTE, routePaymentCallbacks, waitForPaymentResult, waitForConfirmedOrder, waitForAppReturn } from './payment.js';

// Run from the repository root. Keep the API target on the dev environment.
// Example:
// APP_URL=https://www.setty.cloud \
// API_BASE_URL=https://www.setty.cloud/dev \
// USERS_CSV="$PWD/.local/k6-test-users.csv" \
// LISTINGS_CSV="$PWD/.local/k6-test-listings-<RUN_TAG>.csv" \
// K6_BROWSER_HEADLESS=false scripts/payment-conversion/run_scenario.sh
// Add ANALYTICS_SMOKE=true to verify one buyer's login and product-view delivery first.
//
// The C cohort waits for a real Toss test-payment result in a visible browser.
// Toss payment authentication is not automated yet; never store card details in this script.

const APP_URL = (__ENV.APP_URL || 'https://www.setty.cloud').replace(/\/+$/, '');
const API_BASE_URL = (__ENV.API_BASE_URL || 'https://www.setty.cloud/dev').replace(/\/+$/, '');
const USERS_CSV = __ENV.USERS_CSV || '.local/k6-test-users.csv';
const LISTINGS_CSV = __ENV.LISTINGS_CSV;
const PAYMENT_TIMEOUT_MS = Number(__ENV.PAYMENT_TIMEOUT_MS || 3 * 60 * 1000);
const POSTHOG_TIMEOUT_MS = Number(__ENV.POSTHOG_TIMEOUT_MS || 30_000);
const ALLOW_NON_DEV_API = __ENV.ALLOW_NON_DEV_API === 'true';
const ANALYTICS_SMOKE = __ENV.ANALYTICS_SMOKE === 'true';
const PURCHASE_SMOKE = __ENV.PURCHASE_SMOKE === 'true';
const K6_PHASE = ANALYTICS_SMOKE ? 'smoke' : PURCHASE_SMOKE ? 'purchase' : __ENV.K6_PHASE;

function failConfiguration(message) {
    throw new Error(message);
}

if (!/^https?:\/\//.test(APP_URL) || !/^https?:\/\//.test(API_BASE_URL)) {
    failConfiguration('APP_URL과 API_BASE_URL은 http:// 또는 https://로 시작해야 합니다.');
}
const APP_ORIGIN_MATCH = APP_URL.match(/^(https?:\/\/[^/?#]+)/i);
if (!APP_ORIGIN_MATCH) {
    failConfiguration('APP_URL에서 서비스 origin을 확인할 수 없습니다.');
}
const APP_ORIGIN = APP_ORIGIN_MATCH[1].toLowerCase();
if (!ALLOW_NON_DEV_API && !/\/dev$/i.test(API_BASE_URL)) {
    failConfiguration('API_BASE_URL은 기본적으로 /dev 환경만 허용합니다. 의도적으로 다른 환경을 쓸 때만 ALLOW_NON_DEV_API=true를 지정하세요.');
}
if (!LISTINGS_CSV) {
    failConfiguration('LISTINGS_CSV에 준비된 매물 CSV 경로를 지정하세요.');
}
if (!Number.isFinite(PAYMENT_TIMEOUT_MS) || PAYMENT_TIMEOUT_MS < 60_000) {
    failConfiguration('PAYMENT_TIMEOUT_MS는 60000 이상이어야 합니다.');
}
if (!Number.isFinite(POSTHOG_TIMEOUT_MS) || POSTHOG_TIMEOUT_MS < 10_000) {
    failConfiguration('POSTHOG_TIMEOUT_MS는 10000 이상이어야 합니다.');
}
if (!ANALYTICS_SMOKE && !['pre_purchase', 'purchase'].includes(K6_PHASE)) {
    failConfiguration('전체 시나리오는 scripts/payment-conversion/run_scenario.sh로 실행하세요. 구매 단계는 앞 단계가 성공적으로 끝난 뒤 시작됩니다.');
}

function parseCsv(text, expectedHeader, sourceName) {
    const lines = text.trim().split(/\r?\n/).filter(Boolean);
    const expectedColumns = expectedHeader.split(',').length;
    if (lines.length < 2 || lines[0] !== expectedHeader) {
        failConfiguration(`${sourceName} 헤더가 ${expectedHeader} 형식이 아닙니다.`);
    }
    return lines.slice(1).map((line, index) => {
        const columns = line.split(',');
        if (columns.length !== expectedColumns) {
            failConfiguration(`${sourceName} ${index + 2}행의 열 수가 올바르지 않습니다.`);
        }
        return columns;
    });
}

const users = new SharedArray('k6 buyer accounts', () => parseCsv(
    open(USERS_CSV),
    'loginId,password',
    '구매자 CSV'
).map(([loginId, password]) => ({ loginId, password })));

const listings = new SharedArray('k6 listings', () => parseCsv(
    open(LISTINGS_CSV),
    'listingId,title,category,price',
    '매물 CSV'
).map(([listingId, title, category, price]) => ({
    id: Number(listingId),
    title,
    category,
    price: Number(price)
})));

const wauUsers = new Counter('scenario_wau_users');
const productViews = new Counter('scenario_product_views');
const checkoutEntries = new Counter('scenario_checkout_entries');
const checkoutAbandons = new Counter('scenario_checkout_abandons');
const reconfirmUsers = new Counter('scenario_reconfirm_users');
const successfulPayments = new Counter('scenario_payment_successes');
const verifiedPostHogEvents = new Counter('scenario_posthog_verified_events');

const BROWSER_OPTIONS = { browser: { type: 'chromium' } };
// 결제창을 열면 6분짜리 PENDING 주문이 매물을 잡는다. B(결제창 이탈 30명)와 C(결제 20명)가
// 같은 매물을 다시 쓰면 ALREADY_ORDERED가 나므로, 매물 CSV의 1~30번은 B, 31~50번은 C에만 배정한다.
const PURCHASE_LISTING_OFFSET = 30;
const SCENARIO_LISTINGS = 50;

export const options = {
    scenarios: ANALYTICS_SMOKE ? {
        analytics_smoke: {
            executor: 'per-vu-iterations',
            vus: 1,
            iterations: 1,
            maxDuration: '1m',
            exec: 'browseOnly',
            options: BROWSER_OPTIONS
        }
    } : PURCHASE_SMOKE ? {
        purchase_smoke: {
            executor: 'per-vu-iterations',
            vus: 1,
            iterations: 1,
            maxDuration: '5m',
            exec: 'completePurchase',
            options: BROWSER_OPTIONS
        }
    } : K6_PHASE === 'pre_purchase' ? {
        browse_only: {
            executor: 'per-vu-iterations',
            vus: 2,
            iterations: 25,
            maxDuration: '8m',
            exec: 'browseOnly',
            options: BROWSER_OPTIONS
        },
        checkout_abandon: {
            executor: 'per-vu-iterations',
            vus: 2,
            iterations: 15,
            maxDuration: '8m',
            exec: 'checkoutAbandon',
            options: BROWSER_OPTIONS
        },
    } : {
        purchase: {
            executor: 'per-vu-iterations',
            vus: 2,
            iterations: 10,
            maxDuration: '20m',
            gracefulStop: '15m',
            exec: 'completePurchase',
            options: BROWSER_OPTIONS
        }
    },
    thresholds: ANALYTICS_SMOKE ? {
        checks: ['rate==1.0'],
        scenario_wau_users: ['count==1'],
        scenario_posthog_verified_events: ['count==2']
    } : PURCHASE_SMOKE ? {
        checks: ['rate==1.0'],
        scenario_wau_users: ['count==1'],
        scenario_product_views: ['count==1'],
        scenario_checkout_entries: ['count==1'],
        scenario_payment_successes: ['count==1'],
        scenario_posthog_verified_events: ['count==4']
    } : K6_PHASE === 'pre_purchase' ? {
        checks: ['rate==1.0'],
        scenario_wau_users: ['count==80'],
        scenario_product_views: ['count==90'],
        scenario_checkout_entries: ['count==30'],
        scenario_checkout_abandons: ['count==30'],
        scenario_reconfirm_users: ['count==10'],
        // 80 identify + 90 product views + 30 checkout opens + 30 closes.
        scenario_posthog_verified_events: ['count==230']
    } : {
        checks: ['rate==1.0'],
        scenario_wau_users: ['count==20'],
        scenario_product_views: ['count==20'],
        scenario_checkout_entries: ['count==20'],
        scenario_payment_successes: ['count==20'],
        // 20 identify + 20 product views + 20 checkout opens + 20 payments.
        scenario_posthog_verified_events: ['count==80']
    }
};

export function setup() {
    const requiredUsers = ANALYTICS_SMOKE || PURCHASE_SMOKE ? 1 : 100;
    const requiredListings = ANALYTICS_SMOKE || PURCHASE_SMOKE ? 1 : SCENARIO_LISTINGS;
    if (users.length < requiredUsers) {
        failConfiguration(`구매자 계정은 최소 ${requiredUsers}개가 필요합니다. 현재 ${users.length}개입니다.`);
    }
    if (listings.length < requiredListings || listings.slice(0, requiredListings).some((listing) => !Number.isSafeInteger(listing.id) || listing.id <= 0)) {
        failConfiguration(`매물 ID가 ${requiredListings}개 필요합니다. 현재 CSV 행은 ${listings.length}개입니다.`);
    }
    if (users.slice(0, requiredUsers).some((user) => !user.loginId || !user.password)) {
        failConfiguration('구매자 CSV의 loginId 또는 password가 비어 있습니다.');
    }
    if (new Set(users.slice(0, requiredUsers).map((user) => user.loginId)).size !== requiredUsers) {
        failConfiguration(`구매자 CSV의 첫 ${requiredUsers}개 계정 loginId가 중복됩니다.`);
    }
    if (new Set(listings.slice(0, requiredListings).map((listing) => listing.id)).size !== requiredListings) {
        failConfiguration(`매물 CSV의 첫 ${requiredListings}개 listingId가 중복됩니다.`);
    }
    if (__ENV.K6_BROWSER_HEADLESS !== 'false') {
        failConfiguration('PostHog의 HeadlessChrome 차단과 토스 테스트 결제 확인을 위해 K6_BROWSER_HEADLESS=false로 실행하세요.');
    }
    return { ready: true };
}

function accountAt(index) {
    if (!Number.isSafeInteger(index) || index < 0 || index >= 100) {
        failConfiguration(`시나리오 사용자 인덱스가 범위를 벗어났습니다: ${index}`);
    }
    return users[index];
}

function listingAt(index) {
    return listings[index % listings.length];
}

function apiRoutingInitScript() {
    return `(() => {
        if (window.location.origin.toLowerCase() !== ${JSON.stringify(APP_ORIGIN)}) return;
        const apiBase = ${JSON.stringify(API_BASE_URL)};
        const originalFetch = window.fetch.bind(window);
        window.fetch = (input, init) => {
            const inputUrl = typeof input === 'string'
                ? input
                : input instanceof URL
                    ? input.href
                    : input.url;
            const parsed = new URL(inputUrl, window.location.origin);
            if (parsed.origin !== window.location.origin || !parsed.pathname.startsWith('/api/')) {
                return originalFetch(input, init);
            }
            const rewrittenUrl = apiBase + parsed.pathname + parsed.search;
            if (input instanceof Request) {
                return originalFetch(new Request(rewrittenUrl, input), init);
            }
            return originalFetch(rewrittenUrl, init);
        };
    })();`;
}

async function openPageAndLogin(account) {
    const page = await browser.newPage();
    page.setDefaultTimeout(30_000);
    try {
        // Install before app scripts run so initial data loads and login both target dev.
        await page.context().addInitScript(postHogInitScript(APP_ORIGIN));
        await page.context().addInitScript(apiRoutingInitScript());
        await page.goto(`${APP_URL}/?auth=login`, { waitUntil: 'domcontentloaded' });
        await page.locator('input[name="loginId"]').fill(account.loginId);
        await page.locator('input[name="password"]').fill(account.password);
        await page.locator('[role="dialog"] button[type="submit"]').click();
        await page.waitForFunction(() => Boolean(window.sessionStorage.getItem('setty:auth-token')));
        await page.locator('[role="dialog"]').waitFor({ state: 'detached' });
        // Identify must reach PostHog before a full navigation can discard its queue.
        await confirmPostHogEvent(page, '$identify', account.loginId);
        return page;
    } catch (error) {
        await closeBrowserSession(page);
        throw error;
    }
}

async function closeBrowserSession(page) {
    // windowTarget=self keeps Toss in this page. Close the page and let k6 clean up
    // its iteration-managed BrowserContext after the iteration ends.
    try {
        await page.close();
    } catch (error) {
        const message = error instanceof Error ? error.message : String(error);
        if (!/context canceled|target closed|already closed/i.test(message)) {
            console.warn(`브라우저 컨텍스트 종료 중 오류: ${message}`);
        }
    }
}

async function confirmPostHogEvent(page, eventName, loginId, previousCount = 0, listingId) {
    try {
        await waitForPostHogEvent(page, eventName, loginId, previousCount, listingId, POSTHOG_TIMEOUT_MS);
        check(true, { [`PostHog ${eventName} 수집 성공`]: (value) => value });
        verifiedPostHogEvents.add(1, { event: eventName });
        if (ANALYTICS_SMOKE) console.log(`PostHog 수집 확인: ${eventName}, distinct_id=${loginId}, HTTP 2xx`);
    } catch (error) {
        check(false, { [`PostHog ${eventName} 수집 성공`]: (value) => value });
        throw error;
    }
}

async function visitListing(page, listing, loginId, confirmDelivery = true) {
    const previousViews = await postHogEventCount(page, 'product_viewed', loginId, listing.id);
    await page.goto(`${APP_URL}/listings/${listing.id}`, { waitUntil: 'domcontentloaded' });
    const title = page.locator('#product-title');
    await title.waitFor({ state: 'visible' });
    const renderedTitle = await title.textContent();
    const visible = Boolean(renderedTitle && renderedTitle.trim().length > 0);
    const matchesCsv = renderedTitle?.trim() === listing.title;
    const passed = check(page, {
        '상품 상세가 로드됨': () => visible,
        'CSV의 매물이 표시됨': () => matchesCsv
    });
    if (confirmDelivery && passed && visible && matchesCsv) {
        await confirmPostHogEvent(page, 'product_viewed', loginId, previousViews, listing.id);
    }
    return passed && visible && matchesCsv;
}

async function openCheckout(page, listingId) {
    const purchaseButton = page.getByRole('button', { name: '결제하고 주문하기', exact: true });
    await purchaseButton.waitFor({ state: 'visible', timeout: 10_000 });
    if (!(await purchaseButton.isEnabled())) {
        throw new Error(`매물을 결제할 수 없습니다. listingId=${listingId}, url=${page.url()}`);
    }
    await purchaseButton.click();

    const checkout = page.locator(CHECKOUT_SELECTOR);
    try {
        await checkout.waitFor({ state: 'visible', timeout: 10_000 });
    } catch (error) {
        const cause = error instanceof Error ? error.message : String(error);
        throw new Error(`결제 모달이 열리지 않았습니다. listingId=${listingId}, url=${page.url()}, title=${await page.title()}, cause=${cause}`);
    }
    return checkout;
}

async function browse(accountIndex, cohort) {
    const account = accountAt(accountIndex);
    const listing = listingAt(accountIndex % 20);
    const page = await openPageAndLogin(account);
    try {
        const loaded = await visitListing(page, listing, account.loginId);
        check(loaded, { '상품 조회 성공': (value) => value });
        if (loaded) {
            wauUsers.add(1, { cohort });
            productViews.add(1, { cohort });
        }
    } finally {
        await closeBrowserSession(page);
    }
}

export async function browseOnly() {
    await browse(exec.scenario.iterationInTest, 'A');
}

export async function checkoutAbandon() {
    const iteration = exec.scenario.iterationInTest;
    const accountIndex = 50 + iteration;
    const account = accountAt(accountIndex);
    const listing = listings[iteration];
    const page = await openPageAndLogin(account);
    try {
        const loaded = await visitListing(page, listing, account.loginId, false);
        check(loaded, { '상품 조회 성공': (value) => value });
        if (!loaded) return;
        wauUsers.add(1, { cohort: 'B' });
        productViews.add(1, { cohort: 'B' });

        await openCheckout(page, listing.id);
        checkoutEntries.add(1, { cohort: 'B' });
        await page.waitForTimeout(500);
        try {
            await closeCheckout(page, { loginId: account.loginId, listingId: listing.id });
            check(true, { '결제창 닫힘 확인': (value) => value });
        } catch (error) {
            check(false, { '결제창 닫힘 확인': (value) => value });
            throw error;
        }
        checkoutAbandons.add(1, { cohort: 'B' });
        // These actions stay in one document. Verify their batch before a
        // reconfirmation navigation or closing the page, rather than after every click.
        await confirmPostHogEvent(page, 'product_viewed', account.loginId, 0, listing.id);
        await confirmPostHogEvent(page, 'checkout_opened', account.loginId, 0, listing.id);
        await confirmPostHogEvent(page, 'checkout_closed', account.loginId, 0, listing.id);

        if (iteration < 10) {
            const reconfirmed = await visitListing(page, listing, account.loginId);
            check(reconfirmed, { '재확인 상품 조회 성공': (value) => value });
            if (reconfirmed) {
                productViews.add(1, { cohort: 'B', action: 'reconfirm' });
                reconfirmUsers.add(1);
            }
        }
    } finally {
        await closeBrowserSession(page);
    }
}

export async function completePurchase() {
    const iteration = exec.scenario.iterationInTest;
    const accountIndex = PURCHASE_SMOKE ? iteration : 80 + iteration;
    const account = accountAt(accountIndex);
    // Reserve one distinct listing for each successful purchase.
    const listing = listings[PURCHASE_SMOKE ? iteration : PURCHASE_LISTING_OFFSET + iteration];
    const page = await openPageAndLogin(account);
    try {
        const loaded = await visitListing(page, listing, account.loginId, false);
        check(loaded, { '구매 상품 상세가 로드됨': (value) => value });
        if (!loaded) return;
        wauUsers.add(1, { cohort: 'C' });
        productViews.add(1, { cohort: 'C' });

        const checkout = await openCheckout(page, listing.id);
        checkoutEntries.add(1, { cohort: 'C' });
        await confirmPostHogEvent(page, 'product_viewed', account.loginId, 0, listing.id);
        await confirmPostHogEvent(page, 'checkout_opened', account.loginId, 0, listing.id);

        // The payment UI is still Toss's real test flow; never fake a successful payment.
        const payment = await routePaymentCallbacks(page, { appUrl: APP_URL, appOrigin: APP_ORIGIN, apiBaseUrl: API_BASE_URL });
        await checkout.getByRole('button', { name: /결제하기/ }).click();
        console.log(`토스 수동 인증을 기다립니다. loginId=${account.loginId}, listingId=${listing.id}. 인증을 마치면 승인 결과를 자동 확인합니다.`);
        const orderId = await waitForPaymentResult(page, payment, PAYMENT_TIMEOUT_MS);
        await page.unroute(PAYMENT_CALLBACK_ROUTE);
        check(true, { '결제 승인 DONE 확인': (value) => value });
        console.log(`Setty 결제 승인 DONE 확인. 주문 확정을 확인합니다. orderId=${orderId}`);
        await waitForConfirmedOrder(page, payment, listing.id);
        check(true, { '결제 후 주문 CONFIRMED 확인': (value) => value });
        // Count the verified payment before analytics delivery, so a PostHog
        // failure does not make a completed payment look like an unpaid order.
        successfulPayments.add(1, { cohort: 'C' });
        console.log(`결제 성공: orderId=${orderId}, DONE / CONFIRMED. PostHog payment_succeeded 응답을 기다립니다.`);
        await waitForAppReturn(page, APP_ORIGIN);
        await confirmPostHogEvent(page, 'payment_succeeded', account.loginId);
    } finally {
        await closeBrowserSession(page);
    }
}

function metricCount(data, name) {
    return data.metrics[name]?.values?.count ?? 0;
}

export function handleSummary(data) {
    const wau = metricCount(data, 'scenario_wau_users');
    const checkoutCount = metricCount(data, 'scenario_checkout_entries');
    const purchaseCount = metricCount(data, 'scenario_payment_successes');
    const checkoutAbandonsCount = metricCount(data, 'scenario_checkout_abandons');
    const reconfirmCount = metricCount(data, 'scenario_reconfirm_users');
    const productViewCount = metricCount(data, 'scenario_product_views');
    const verifiedEventCount = metricCount(data, 'scenario_posthog_verified_events');
    const expected = ANALYTICS_SMOKE
        ? { wau: 1, views: 1, checkout: 0, abandons: 0, reconfirm: 0, payments: 0, events: 2 }
        : PURCHASE_SMOKE
            ? { wau: 1, views: 1, checkout: 1, abandons: 0, reconfirm: 0, payments: 1, events: 4 }
            : K6_PHASE === 'pre_purchase'
                ? { wau: 80, views: 90, checkout: 30, abandons: 30, reconfirm: 10, payments: 0, events: 230 }
                : { wau: 20, views: 20, checkout: 20, abandons: 0, reconfirm: 0, payments: 20, events: 80 };
    const checksRate = data.metrics.checks?.values?.rate ?? 0;
    const testPassed = checksRate === 1
        && wau === expected.wau
        && productViewCount === expected.views
        && checkoutCount === expected.checkout
        && checkoutAbandonsCount === expected.abandons
        && reconfirmCount === expected.reconfirm
        && purchaseCount === expected.payments
        && verifiedEventCount === expected.events;
    const percent = (numerator, denominator) => denominator === 0 ? '0.0%' : `${(100 * numerator / denominator).toFixed(1)}%`;

    const title = ANALYTICS_SMOKE
        ? 'k6 PostHog 수집 확인 결과 (1계정, 상품 조회만)'
        : PURCHASE_SMOKE ? 'k6 결제 스모크 결과 (1계정)'
            : K6_PHASE === 'pre_purchase' ? 'k6 선행 시나리오 결과 (상품 조회·결제창 이탈)'
            : 'k6 구매 시나리오 결과';
    const summary = [
        title,
        `테스트 판정: ${testPassed ? '성공' : '실패'}`,
        `WAU (상품을 확인한 고유 계정): ${wau}`,
        `상품 조회 이벤트: ${productViewCount}`,
        `결제창 진입: ${checkoutCount}`,
        `결제창 이탈: ${checkoutAbandonsCount}`,
        `재확인 사용자: ${reconfirmCount}`,
        `결제 완료: ${purchaseCount}`,
        `PostHog HTTP 2xx로 확인한 이벤트: ${verifiedEventCount}`,
        `WAU 대비 최종 결제 전환율: ${percent(purchaseCount, wau)}`,
        `결제창 진입 대비 결제 전환율: ${percent(purchaseCount, checkoutCount)}`,
        'PostHog 집계는 동일한 기간과 k6 테스트 계정(loginId k6user*)으로 필터링해 대조하세요.'
    ].join('\n');
    return { stdout: `${summary}\n` };
}
