import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const cases = ['success', 'declined', 'cancelled', 'html', 'wrong_order', 'pending', 'expired'];
const orders = new Map(cases.map((name, index) => [101 + index, { name, listingId: 501 + index, approved: false, reads: 0 }]));
const confirms = [];
const failures = [];
const analytics = [];
let unintendedCallbackRequests = 0;
const json = (response, status, body) => {
    response.writeHead(status, { 'Content-Type': 'application/json' });
    response.end(JSON.stringify(body));
};
const html = (response, body) => {
    response.writeHead(200, { 'Content-Type': 'text/html' });
    response.end(`<!doctype html><html><body>${body}</body></html>`);
};

const app = createServer(async (request, response) => {
    const url = new URL(request.url, 'http://fixture');
    if (request.method === 'GET' && url.pathname === '/setup') {
        return html(response, '<script>sessionStorage.setItem("setty:auth-token", "fixture-token");</script>');
    }
    if (url.pathname.startsWith('/payments/')) {
        unintendedCallbackRequests++;
        return json(response, 500, { code: 'WRONG_BACKEND' });
    }
    if (url.pathname === '/capture') {
        let body = '';
        for await (const chunk of request) body += chunk;
        analytics.push(JSON.parse(body));
        return json(response, 200, { status: 1 });
    }
    if (url.pathname.startsWith('/dev/api/')) {
        if (request.headers.authorization !== 'Bearer fixture-token') return json(response, 401, { code: 'INVALID_TOKEN' });
        let body = '';
        for await (const chunk of request) body += chunk;
        const payload = body ? JSON.parse(body) : {};
        if (url.pathname === '/dev/api/payments/confirm' && request.method === 'POST') {
            const orderId = Number(payload.orderId?.split('_')[0]);
            const order = orders.get(orderId);
            assert.ok(order);
            assert.equal(payload.amount, 1000);
            assert.equal(payload.paymentKey, 'fixture-payment-key');
            confirms.push(orderId);
            if (order.name === 'declined') return json(response, 400, { code: 'PAYMENT_CONFIRM_FAILED' });
            if (order.name === 'html') return html(response, 'SPA fallback');
            order.approved = true;
            return json(response, 200, { orderId: order.name === 'wrong_order' ? 999 : orderId, status: 'DONE', amount: 1000 });
        }
        if (url.pathname === '/dev/api/payments/fail' && request.method === 'POST') {
            failures.push(payload.orderId);
            response.writeHead(204);
            return response.end();
        }
        const orderMatch = url.pathname.match(/^\/dev\/api\/orders\/(\d+)$/);
        if (orderMatch && request.method === 'GET') {
            const orderId = Number(orderMatch[1]);
            const order = orders.get(orderId);
            assert.ok(order);
            order.reads++;
            const orderStatus = order.name === 'expired' ? 'EXPIRED'
                : order.approved && order.name !== 'pending' && order.reads > 1 ? 'CONFIRMED' : 'PENDING';
            return json(response, 200, { id: orderId, listing: { id: order.listingId }, orderStatus });
        }
        return json(response, 404, { code: 'NOT_FOUND' });
    }
    // Match the observed dev callback fallback: HTML 200 without approval. The
    // app's regular result handler emits telemetry and immediately clears query.
    return html(response, `<script>
        const params = new URLSearchParams(location.search);
        const payment = params.get('payment');
        const orderId = params.get('orderId');
        history.replaceState(null, '', '/');
        if (payment) fetch('/capture', { method: 'POST', body: JSON.stringify({
            event: payment === 'success' ? 'payment_succeeded' : 'payment_failed',
            properties: { distinct_id: 'fixture-buyer', order_id: orderId }
        }) });
    </script>`);
});
let appUrl;
const toss = createServer((request, response) => {
    const url = new URL(request.url, 'http://fixture');
    const testCase = url.searchParams.get('case');
    const orderId = 101 + cases.indexOf(testCase);
    const query = testCase === 'cancelled'
        ? `fallbackOrderId=${orderId}_fixture&code=PAY_PROCESS_CANCELED`
        : `orderId=${orderId}_fixture&paymentKey=fixture-payment-key&amount=1000`;
    return html(response, `<a href="${appUrl}/payments/${testCase === 'cancelled' ? 'fail' : 'success'}?${query}">테스트 인증 완료</a>`);
});
const listen = (server) => new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
await listen(app);
appUrl = `http://127.0.0.1:${app.address().port}`;
await listen(toss);
try {
    const child = spawn('k6', ['run', '--quiet', '--env', `FIXTURE_APP_URL=${appUrl}`, '--env', `FIXTURE_TOSS_URL=http://127.0.0.1:${toss.address().port}`,
        fileURLToPath(new URL('./payment_callback.browser.js', import.meta.url))], {
        env: { ...process.env, K6_BROWSER_HEADLESS: 'true' }, stdio: 'inherit'
    });
    const code = await new Promise((resolve, reject) => {
        child.on('error', reject);
        child.on('close', resolve);
    });
    assert.equal(code, 0, 'k6 결제 복귀 회귀 테스트 실패');
    assert.equal(unintendedCallbackRequests, 0, '공유 앱의 기본 콜백으로 결제를 승인하면 안 됩니다.');
    assert.deepEqual(confirms, [101, 102, 104, 105, 106, 107]);
    assert.deepEqual(failures, ['103_fixture']);
    assert.ok(orders.get(101).reads >= 2, '비동기 주문 확정을 기다려야 합니다.');
    assert.equal(orders.get(102).reads, 0, '승인 실패 후 주문 성공을 확인하면 안 됩니다.');
    assert.equal(orders.get(104).reads, 0, 'HTML 200을 승인 성공으로 처리하면 안 됩니다.');
    assert.equal(orders.get(105).reads, 0, '다른 주문의 DONE 응답을 성공으로 처리하면 안 됩니다.');
    assert.ok(analytics.some((event) => event.event === 'payment_succeeded' && event.properties.order_id === '101_fixture'));
    assert.ok(!analytics.some((event) => event.event === 'payment_succeeded' && ['102_fixture', '103_fixture', '104_fixture', '105_fixture'].includes(event.properties.order_id)));
    console.log('결제 복귀 회귀 테스트 통과: 7개 사례, 실제 k6 브라우저·로컬 API·이벤트 HTTP 응답 검증');
} finally {
    app.closeAllConnections();
    toss.closeAllConnections();
    await Promise.all([new Promise((resolve) => app.close(resolve)), new Promise((resolve) => toss.close(resolve))]);
}
