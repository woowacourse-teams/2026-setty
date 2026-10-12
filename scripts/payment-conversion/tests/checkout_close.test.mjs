import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const analytics = [];
const app = createServer(async (request, response) => {
    const url = new URL(request.url, 'http://fixture');
    if (url.pathname === '/capture') {
        let body = '';
        for await (const chunk of request) body += chunk;
        analytics.push(JSON.parse(body));
        response.writeHead(200, { 'Content-Type': 'application/json' });
        return response.end('{"status":1}');
    }
    response.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
    response.end(`<!doctype html><html><body>
        <div role="dialog" aria-label="결제"><button aria-label="결제 닫기">닫기</button></div>
        <script>
            const name = new URLSearchParams(location.search).get('case');
            const dialog = document.querySelector('[role="dialog"]');
            window.fixtureClicks = 0;
            document.querySelector('button').addEventListener('click', () => {
                window.fixtureClicks++;
                if (name === 'stuck' || (name === 'missed_click' && window.fixtureClicks === 1)) return;
                const close = () => {
                    dialog.remove();
                    fetch('/capture', { method: 'POST', body: JSON.stringify({
                        event: 'checkout_closed', uuid: name,
                        properties: { distinct_id: 'fixture-' + name, listing_id: 501 }
                    }) });
                };
                if (name === 'paused_raf') window.requestAnimationFrame = () => 0;
                if (name === 'delayed' || name === 'paused_raf') setTimeout(close, 200);
                else close();
            });
        </script>
    </body></html>`);
});

await new Promise((resolve) => app.listen(0, '127.0.0.1', resolve));
try {
    const child = spawn('k6', ['run', '--quiet', '--env', `FIXTURE_APP_URL=http://127.0.0.1:${app.address().port}`,
        fileURLToPath(new URL('./checkout_close.browser.js', import.meta.url))], {
        env: { ...process.env, K6_BROWSER_HEADLESS: 'true' }, stdio: 'inherit'
    });
    const code = await new Promise((resolve, reject) => {
        child.on('error', reject);
        child.on('close', resolve);
    });
    assert.equal(code, 0, 'k6 결제창 닫기 회귀 테스트 실패');
    assert.deepEqual(analytics.map((event) => event.properties.distinct_id), [
        'fixture-normal', 'fixture-missed_click', 'fixture-delayed', 'fixture-paused_raf'
    ]);
    console.log('결제창 닫기 회귀 테스트 통과: 5개 사례, 실제 k6 클릭·DOM 제거·이탈 이벤트 검증');
} finally {
    app.closeAllConnections();
    await new Promise((resolve) => app.close(resolve));
}
