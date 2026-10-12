import http from 'k6/http';

export const PAYMENT_CALLBACK_ROUTE = /\/payments\/(success|fail)(?:\?[^#]*)?$/;

function paymentCallback(url, appOrigin) {
    const match = url.match(/^(https?:\/\/[^/?#]+)\/payments\/(success|fail)(?:\?([^#]*))?$/i);
    if (!match || match[1].toLowerCase() !== appOrigin) return null;
    const params = {};
    for (const pair of (match[3] || '').split('&').filter(Boolean)) {
        const separator = pair.indexOf('=');
        const key = separator < 0 ? pair : pair.slice(0, separator);
        const value = separator < 0 ? '' : pair.slice(separator + 1);
        params[decodeURIComponent(key)] = decodeURIComponent(value.replace(/\+/g, ' '));
    }
    return { ...params, result: match[2].toLowerCase() };
}

function jsonBody(response) {
    try {
        return response.json();
    } catch (_) {
        return null;
    }
}

function apiRequest(payment, method, path, body, timeoutMs = 20_000) {
    return http.asyncRequest(method, `${payment.apiBaseUrl}${path}`, body === undefined ? null : JSON.stringify(body), {
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${payment.token}` },
        redirects: 0,
        timeout: `${timeoutMs}ms`,
        tags: { name: path.replace(/\/\d+$/, '/:id') }
    });
}

// /dev/payments/** currently falls through to the shared SPA. Use the existing
// authenticated API instead, with Toss's actual authentication result. Only a
// verified DONE response may redirect to the app's normal success handler.
export async function routePaymentCallbacks(page, { appUrl, appOrigin, apiBaseUrl }) {
    const token = await page.evaluate(() => window.sessionStorage.getItem('setty:auth-token'));
    if (!token) throw new Error('결제 승인에 사용할 구매자 로그인 토큰이 없습니다.');
    const payment = { token, apiBaseUrl, phase: 'authentication', result: null, orderId: null, error: null };

    await page.route(PAYMENT_CALLBACK_ROUTE, async (route) => {
        let callback;
        let result = 'fail';
        let reason = 'K6_PAYMENT_CONFIRM_FAILED';
        try {
            callback = paymentCallback(route.request().url(), appOrigin);
            if (!callback) {
                await route.continue();
                return;
            }
            const tossOrderId = callback.orderId || callback.fallbackOrderId;
            payment.orderId = Number(tossOrderId?.split('_')[0]);
            if (!Number.isSafeInteger(payment.orderId) || payment.orderId <= 0) {
                throw new Error('토스 복귀 요청에 유효한 주문 ID가 없습니다.');
            }

            if (callback.result === 'fail') {
                const response = await apiRequest(payment, 'POST', '/api/payments/fail', { orderId: tossOrderId });
                reason = callback.code || 'PAY_PROCESS_CANCELED';
                if (response.status !== 204) {
                    throw new Error(`결제 실패 기록 API 오류: orderId=${payment.orderId}, HTTP=${response.status}, code=${jsonBody(response)?.code || 'UNKNOWN'}`);
                }
                payment.error = `토스 테스트 결제가 취소되었거나 실패했습니다. orderId=${payment.orderId}, code=${reason}`;
            } else {
                const amount = Number(callback.amount);
                if (!callback.paymentKey || !Number.isSafeInteger(amount) || amount <= 0) {
                    throw new Error(`토스 인증 복귀에 결제 키 또는 유효한 금액이 없습니다. orderId=${payment.orderId}`);
                }
                payment.phase = 'approval';
                console.log(`토스 인증 복귀 확인. 결제 승인 API를 호출합니다. orderId=${payment.orderId}`);
                const response = await apiRequest(payment, 'POST', '/api/payments/confirm', {
                    paymentKey: callback.paymentKey, orderId: tossOrderId, amount
                });
                const body = jsonBody(response);
                if (response.status !== 200 || body?.status !== 'DONE'
                    || body.orderId !== payment.orderId || body.amount !== amount) {
                    reason = body?.code || 'K6_PAYMENT_CONFIRM_FAILED';
                    throw new Error(`결제 승인 API 확인 실패: orderId=${payment.orderId}, HTTP=${response.status}, code=${reason}, status=${body?.status || '없음'}`);
                }
                result = 'success';
            }
        } catch (error) {
            payment.error = error instanceof Error ? error.message : '결제 복귀 처리 중 오류가 발생했습니다.';
        }

        const orderId = callback?.orderId || callback?.fallbackOrderId || '';
        const location = `${appUrl}/?payment=${result}&orderId=${encodeURIComponent(orderId)}`
            + (result === 'fail' ? `&reason=${encodeURIComponent(reason)}` : '');
        try {
            await route.fulfill({ status: 302, headers: { Location: location, 'Cache-Control': 'no-store' }, body: '' });
        } catch (_) {
            payment.error = `${payment.error || '결제 승인은 완료되었습니다.'} Setty 복귀 응답을 전달하지 못했습니다.`;
        }
        payment.phase = result === 'success' ? 'approved' : 'failed';
        payment.result = result;
    });
    return payment;
}

export async function waitForPaymentResult(page, payment, timeoutMs) {
    const deadline = Date.now() + timeoutMs;
    while (Date.now() < deadline) {
        if (payment.result) {
            if (payment.error) throw new Error(payment.error);
            return payment.orderId;
        }
        await page.waitForTimeout(250);
    }
    const path = page.url().split(/[?#]/, 1)[0];
    throw new Error(`결제 결과를 ${timeoutMs}ms 안에 확인하지 못했습니다. 단계=${payment.phase}, 마지막 경로=${path}`);
}

export async function waitForConfirmedOrder(page, payment, listingId, timeoutMs = 30_000) {
    const deadline = Date.now() + timeoutMs;
    let lastStatus = '확인 전';
    while (Date.now() < deadline) {
        const response = await apiRequest(payment, 'GET', `/api/orders/${payment.orderId}`, undefined,
            Math.min(10_000, deadline - Date.now()));
        const order = jsonBody(response);
        if (response.status === 200) {
            if (order?.id !== payment.orderId || order.listing?.id !== listingId) {
                throw new Error(`승인한 주문과 조회한 주문·매물이 일치하지 않습니다. orderId=${payment.orderId}, listingId=${listingId}`);
            }
            if (order.orderStatus === 'CONFIRMED') return;
            if (order.orderStatus !== 'PENDING') {
                throw new Error(`결제 승인 후 주문이 확정되지 않았습니다. orderId=${payment.orderId}, status=${order.orderStatus}`);
            }
            lastStatus = order.orderStatus;
        } else {
            lastStatus = `HTTP ${response.status}`;
            if (response.status >= 400 && response.status < 500) {
                throw new Error(`결제 후 주문 조회 실패: orderId=${payment.orderId}, ${lastStatus}, code=${order?.code || 'UNKNOWN'}`);
            }
        }
        await page.waitForTimeout(Math.min(1000, Math.max(0, deadline - Date.now())));
    }
    throw new Error(`결제는 DONE이지만 주문 확정을 ${timeoutMs}ms 안에 확인하지 못했습니다. orderId=${payment.orderId}, 마지막 상태=${lastStatus}. PaymentCompleted 이벤트 처리를 확인하세요.`);
}

export async function waitForAppReturn(page, appOrigin, timeoutMs = 30_000) {
    const deadline = Date.now() + timeoutMs;
    while (Date.now() < deadline) {
        try {
            const ready = await page.evaluate((origin) => window.location.origin.toLowerCase() === origin
                && !/^\/payments\//.test(window.location.pathname)
                && document.readyState !== 'loading'
                && Boolean(window.sessionStorage.getItem('setty:auth-token')), appOrigin);
            if (ready) return;
        } catch (error) {
            const message = error instanceof Error ? error.message : String(error);
            if (!/cannot find context|same javascript world|execution context was destroyed|context canceled/i.test(message)) throw error;
        }
        await page.waitForTimeout(250);
    }
    throw new Error('결제 승인 후 Setty 화면으로 복귀하지 못했습니다.');
}
