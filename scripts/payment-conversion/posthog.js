// Installed only in k6 browser contexts, before the shared frontend starts.
// Observe the app's real requests; never create analytics events in the test.
function installPostHogObserver(appOrigin) {
    if (window.location.origin.toLowerCase() !== appOrigin) return;

    // PostHog drops all captures when navigator.webdriver is true. Keep this
    // override scoped to the tested frontend; production bot filtering stays enabled.
    Object.defineProperty(navigator, 'webdriver', { configurable: true, get: () => false });

    const storageKey = 'setty:k6-posthog-delivery';
    const readState = () => JSON.parse(sessionStorage.getItem(storageKey) || '{"events":[],"errors":[]}');
    const isCapture = (value) => {
        const url = new URL(value, window.location.href);
        return /\/(e|capture|batch)\/?$/.test(url.pathname);
    };

    async function decodeEvents(body) {
        let text;
        if (typeof body === 'string') {
            text = body;
            if (text.startsWith('data=')) {
                text = new URLSearchParams(text).get('data');
                if (!text.startsWith('{') && !text.startsWith('[')) {
                    text = new TextDecoder().decode(Uint8Array.from(atob(text), (c) => c.charCodeAt(0)));
                }
            }
        } else {
            const blob = body instanceof Blob ? body : new Blob([body]);
            const bytes = new Uint8Array(await blob.arrayBuffer());
            text = bytes[0] === 0x1f && bytes[1] === 0x8b
                ? await new Response(blob.stream().pipeThrough(new DecompressionStream('gzip'))).text()
                : new TextDecoder().decode(bytes);
        }
        const payload = JSON.parse(text);
        const events = Array.isArray(payload) ? payload : payload.batch || [payload];
        // Retain only identifiers needed for verification, never the full payload.
        return events.map((event) => ({
            uuid: event.uuid,
            event: event.event,
            distinctId: event.properties?.distinct_id ?? event.distinct_id,
            listingId: event.properties?.listing_id
        }));
    }

    function record(events, status) {
        const state = readState();
        for (const event of events) {
            const existing = event.uuid && state.events.find((item) => item.uuid === event.uuid);
            if (existing) {
                if (!(existing.status >= 200 && existing.status < 300)) existing.status = status;
            } else {
                state.events.push({ ...event, status });
            }
        }
        sessionStorage.setItem(storageKey, JSON.stringify(state));
    }

    function observe(body, statusPromise) {
        const events = Promise.resolve(body).then(decodeEvents).then((decoded) => {
            record(decoded, -1);
            return decoded;
        });
        Promise.all([events, statusPromise]).then(([decoded, status]) => record(decoded, status)).catch(() => {
            const state = readState();
            state.errors.push('PostHog 요청 본문 또는 응답을 확인하지 못했습니다.');
            sessionStorage.setItem(storageKey, JSON.stringify(state));
        });
    }

    const originalOpen = XMLHttpRequest.prototype.open;
    const originalSend = XMLHttpRequest.prototype.send;
    const xhrUrls = new WeakMap();
    XMLHttpRequest.prototype.open = function (method, url, ...args) {
        xhrUrls.set(this, String(url));
        return originalOpen.call(this, method, url, ...args);
    };
    XMLHttpRequest.prototype.send = function (body) {
        if (isCapture(xhrUrls.get(this))) {
            const status = new Promise((resolve) => this.addEventListener('loadend', () => resolve(this.status), { once: true }));
            observe(body, status);
        }
        return originalSend.call(this, body);
    };

    const originalFetch = window.fetch.bind(window);
    window.fetch = function (input, init) {
        const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url;
        const capture = isCapture(url);
        const body = capture ? init?.body ?? (input instanceof Request ? input.clone().arrayBuffer() : undefined) : undefined;
        const result = originalFetch(input, init);
        if (capture) {
            const status = result.then((response) => response.status, () => 0);
            observe(body, status);
        }
        return result;
    };
}

export function postHogInitScript(appOrigin) {
    return `(${installPostHogObserver.toString()})(${JSON.stringify(appOrigin)});`;
}

async function deliveryState(page) {
    return page.evaluate(() => {
        return JSON.parse(window.sessionStorage.getItem('setty:k6-posthog-delivery') || '{"events":[],"errors":[]}');
    });
}

function matchingEvents(state, eventName, loginId, listingId) {
    return state.events.filter((event) => event.event === eventName && event.distinctId === loginId
        && (listingId === undefined || event.listingId === listingId)
        && event.status >= 200 && event.status < 300);
}

export async function postHogEventCount(page, eventName, loginId, listingId) {
    return matchingEvents(await deliveryState(page), eventName, loginId, listingId).length;
}

export async function waitForPostHogEvent(page, eventName, loginId, previousCount = 0, listingId, timeoutMs = 30_000) {
    const deadline = Date.now() + timeoutMs;
    let state = await deliveryState(page);
    if (matchingEvents(state, eventName, loginId, listingId).length > previousCount) return;
    // The SDK normally flushes batches every 3s. Check once a second instead of
    // unconditionally sleeping past a response that may already have arrived.
    while (true) {
        const remaining = deadline - Date.now();
        if (remaining <= 0) break;
        await page.waitForTimeout(Math.min(1000, remaining));
        state = await deliveryState(page);
        if (matchingEvents(state, eventName, loginId, listingId).length > previousCount) return;
    }
    const responses = state.events.map((event) => `${event.event}:${event.status === -1 ? '응답 대기' : event.status}`).join(', ') || '없음';
    const webdriver = await page.evaluate(() => navigator.webdriver);
    throw new Error(`PostHog 수집 확인 실패(${timeoutMs}ms): ${eventName}, loginId=${loginId}, 응답=${responses}, 관측 오류=${state.errors.length}, webdriver=${webdriver}. 응답 대기는 전송 요청을 관측했으나 HTTP 응답을 아직 확인하지 못한 상태입니다.`);
}
