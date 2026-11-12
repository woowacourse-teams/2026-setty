const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const path = require('node:path');
const { test } = require('node:test');
const { runInNewContext } = require('node:vm');
const ts = require('typescript');
class UncertainSupportResponse extends Error {}
class ApiError extends Error { constructor(code, message, status) { super(message); Object.assign(this, { code, status }); } }
function load(file, globals = {}) {
    const source = readFileSync(path.join(__dirname, '../src/features/support', file), 'utf8');
    const { outputText } = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 } });
    const module = { exports: {} };
    runInNewContext(outputText, { module, exports: module.exports, require: () => ({ ApiError, UncertainSupportResponse, tokenStore: { get: () => 'member' } }), AbortController, setTimeout, clearTimeout, ...globals });
    return module.exports;
}
const timestamp = '2026-11-04T01:00:00Z';
const turn = (id, status = 'SUCCEEDED') => ({ turnId: id, clientRequestId: `request-${id}`, input: { type: 'TEXT', text: '취소 가능해?' }, status, createdAt: timestamp, processingDeadlineAt: timestamp, answer: status === 'SUCCEEDED' ? '확인했어요' : null, cards: [], errorCode: null });
const plain = value => JSON.parse(JSON.stringify(value));
test('턴 병합은 중복을 제거하고 늦은 PROCESSING이 최종 응답을 덮지 않는다', () => {
    const { mergeTurns } = load('supportState.ts');
    const result = mergeTurns([turn(2)], [turn(1), turn(2, 'PROCESSING')]);
    assert.deepEqual(plain(result.map(t => [t.turnId, t.status])), [[1, 'SUCCEEDED'], [2, 'SUCCEEDED']]);
});
test('응답 유실 후 요청 ID가 서버 기록에 있을 때만 pending을 해제한다', () => {
    const { resolvePending, pageTurns } = load('supportState.ts');
    const pending = { clientRequestId: 'request-2', input: { type: 'TEXT', text: '질문' } };
    assert.equal(resolvePending(pending, [turn(1)]), pending);
    assert.equal(resolvePending(pending, pageTurns({ items: [turn(1)], activeTurn: turn(2, 'PROCESSING') })), null);
});
test('API codec은 깨진 카드·턴·날짜를 성공으로 받아들이지 않는다', () => {
    const { isTurn } = load('supportApi.ts');
    assert.equal(isTurn(turn(1)), true);
    assert.equal(isTurn({ ...turn(1), status: 'DONE' }), false);
    assert.equal(isTurn({ ...turn(1), cards: [{ type: 'ELIGIBILITY', eligibility: 'ELIGIBLE' }] }), false);
    assert.equal(isTurn({ ...turn(1), createdAt: 'unknown' }), false);
});
test('본문 유실은 미확인 응답으로 분류하고 동일 요청 ID를 재전송한다', async () => {
    const sent = []; let first = true;
    const { supportApi, UncertainSupportResponse } = load('supportApi.ts', { fetch: async (_url, options) => {
        sent.push(JSON.parse(options.body));
        if (first) { first = false; return { status: 200, ok: true, json: async () => { throw new SyntaxError(); } }; }
        return { status: 200, ok: true, json: async () => turn(1) };
    } });
    const request = { clientRequestId: 'same-id', input: { type: 'TEXT', text: '질문' } };
    await assert.rejects(supportApi.send(10, request), UncertainSupportResponse);
    await supportApi.send(10, request);
    assert.deepEqual(sent.map(x => x.clientRequestId), ['same-id', 'same-id']);
});
test('정상 서버 오류는 접수 여부 미확인 네트워크 오류로 바꾸지 않는다', async () => {
    const { supportApi } = load('supportApi.ts', { fetch: async () => ({ status: 503, ok: false, json: async () => ({ code: 'SUPPORT_DISABLED', message: '잠시 쉬고 있어요' }) }) });
    await assert.rejects(supportApi.create(), error => error instanceof ApiError && error.status === 503);
});
test('다른 계정으로 변경된 뒤의 응답은 폐기한다', async () => {
    let token = 'a';
    const { supportApi } = load('supportApi.ts', {
        require: () => ({ ApiError, UncertainSupportResponse, tokenStore: { get: () => token } }),
        fetch: async () => { token = 'b'; return { status: 200, ok: true, json: async () => turn(1) }; }
    });
    await assert.rejects(supportApi.send(1, { clientRequestId: 'r', input: { type: 'TEXT', text: 'x' } }), error => error.code === 'SUPPORT_SESSION_CHANGED');
});

test('접수 뒤 5xx는 같은 ID를 보존하며 접수 전 AI_DISABLED만 확정 실패다', () => {
    const { isUncertainSendError } = load('supportState.ts');
    assert.equal(isUncertainSendError(new ApiError('INTERNAL_ERROR', 'DB 실패', 500)), true);
    assert.equal(isUncertainSendError(new ApiError('GATEWAY_TIMEOUT', 'timeout', 504)), true);
    assert.equal(isUncertainSendError(new ApiError('AI_DISABLED', 'disabled', 503)), false);
    assert.equal(isUncertainSendError(new ApiError('INVALID_INPUT', 'invalid', 400)), false);
});
test('tokenStore는 현재 탭과 다른 탭 계정 변경을 알리고 구독 해제한다', () => {
    const source = readFileSync(path.join(__dirname, '../src/api/http.ts'), 'utf8');
    const { outputText } = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 } });
    const module = { exports: {} }, values = new Map(), target = new EventTarget();
    const window = { localStorage: { getItem: key => values.get(key), setItem: (key, value) => values.set(key, value), removeItem: key => values.delete(key) }, addEventListener: target.addEventListener.bind(target), removeEventListener: target.removeEventListener.bind(target), dispatchEvent: target.dispatchEvent.bind(target) };
    runInNewContext(outputText, { module, exports: module.exports, window, Event });
    const { tokenStore } = module.exports; let changed = 0;
    const stop = tokenStore.subscribe(() => changed++);
    tokenStore.set('a'); tokenStore.set('b'); assert.equal(changed, 2); assert.equal(tokenStore.get(), 'b');
    const event = new Event('storage'); Object.defineProperty(event, 'key', { value: 'setty:credential-token' }); target.dispatchEvent(event);
    assert.equal(changed, 3);
    stop(); tokenStore.clear(); assert.equal(changed, 3); assert.equal(tokenStore.get(), '');
});
test('상담 목록 opaque cursor와 턴 numeric cursor를 구별한다', () => {
    const { isConversationPage } = load('supportApi.ts');
    assert.equal(isConversationPage({ items: [], nextCursor: 'opaque+/=' }), true);
    assert.equal(isConversationPage({ items: [], nextCursor: 2 }), false);
    const item = { id: 1, title: '새 상담', createdAt: '2026-11-01T00:00:00Z', lastActivityAt: '2026-11-01T00:00:00Z', expiresAt: '2026-12-01T00:00:00Z', hasMessages: false };
    assert.equal(isConversationPage({ items: [item], nextCursor: null }), true);
    const { hasMessages, ...legacyItem } = item;
    assert.equal(isConversationPage({ items: [legacyItem], nextCursor: null }), true);
    assert.equal(isConversationPage({ items: [{ ...item, hasMessages: 'false' }], nextCursor: null }), false);
});
test('PostHog 설정은 상담 DOM을 차단하고 상담 네트워크 요청도 제외한다', () => {
    const source = readFileSync(path.join(__dirname, '../src/analytics/posthog.ts'), 'utf8');
    const { outputText } = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 } });
    const module = { exports: {} }; let config;
    runInNewContext(outputText, { module, exports: module.exports,
        require: name => name === 'posthog-js' ? { default: { init: (_key, options) => { config = options; } } } : {},
        __POSTHOG_KEY__: 'phc_synthetic', __POSTHOG_HOST__: 'http://localhost', URL, window: { location: { origin: 'http://localhost' } }
    });
    module.exports.initializeAnalytics();
    assert.equal(config.session_recording.blockSelector, '.support-private');
    assert.equal(config.session_recording.maskTextSelector, '.support-private');
    const request = { name: 'http://localhost/api/support/conversations/1/messages', requestBody: '민감한 합성 질문' };
    assert.equal(config.session_recording.maskCapturedNetworkRequestFn(request), undefined);
    const ordinary = { name: 'http://localhost/api/listings' };
    assert.equal(config.session_recording.maskCapturedNetworkRequestFn(ordinary), ordinary);
});

test('과거 테스트 한도 실패는 현재 재시도를 요구하거나 계속 차단 중이라고 단정하지 않는다', () => {
    const { failureMessage } = load('supportState.ts');
    const message = failureMessage('LOCAL_MODEL_BUDGET_EXHAUSTED');
    assert.match(message, /테스트 호출 한도/);
    assert.doesNotMatch(message, /다시 물어|새 상담|현재|초기화/);
    assert.match(failureMessage('LOOKUP_FAILED'), /주문은 변경하지 않았어요/);
});

test('Enter 전송은 준비된 입력에서만 허용하고 Shift/IME 확정 Enter는 보내지 않는다', () => {
    const { isMessageSubmitKey } = load('supportState.ts');
    const enter = { key: 'Enter', shiftKey: false, isComposing: false, keyCode: 13 };
    assert.equal(isMessageSubmitKey(enter, false, true), true);
    assert.equal(isMessageSubmitKey({ ...enter, shiftKey: true }, false, true), false);
    assert.equal(isMessageSubmitKey({ ...enter, isComposing: true }, false, true), false);
    assert.equal(isMessageSubmitKey({ ...enter, keyCode: 229 }, false, true), false);
    assert.equal(isMessageSubmitKey(enter, true, true), false);
    assert.equal(isMessageSubmitKey(enter, false, false), false);
    assert.equal(isMessageSubmitKey({ ...enter, key: 'a' }, false, true), false);
});

test('서버 주문 날짜와 기존 날짜 필드가 없는 과거 카드를 함께 허용한다', () => {
    const { isTurn } = load('supportApi.ts');
    const order = { orderId: 12, title: '의자', createdAt: '2026-11-03T15:00:00Z', orderStatus: 'CONFIRMED' };
    const withOrder = o => ({ ...turn(1), cards: [{ type: 'ORDERS', orders: [o], nextCursor: null, hasUndatedOrders: false }] });
    assert.equal(isTurn(withOrder(order)), true);
    assert.equal(isTurn(withOrder({ ...order, createdDateKst: '2026-11-04' })), true);
    assert.equal(isTurn(withOrder({ ...order, createdAt: null, createdDateKst: null })), true);
    assert.equal(isTurn(withOrder({ ...order, createdDateKst: 'yesterday' })), false);
});

test('더 보기 요청과 새 주문 카드 필드를 검증하고 과거 카드도 허용한다', () => {
    const { isTurn } = load('supportApi.ts');
    const { inputLabel } = load('supportState.ts');
    const next = { ...turn(1), input: {type:'NEXT_ORDERS',sourceTurnId:2} };
    assert.equal(isTurn(next),true); assert.equal(inputLabel(next),'주문 더 보기');
    assert.equal(isTurn({...next,input:{type:'NEXT_ORDERS',sourceTurnId:-1}}),false);
    const item={orderId:3,title:'의자',createdAt:null,orderStatus:'CONFIRMED',thumbnailUrl:'/chair.png',totalPrice:120000,deliveryStatus:'REQUESTED'};
    const card={type:'ORDERS',orders:[item],nextCursor:null,hasUndatedOrders:false,pageSize:10,purpose:'BROWSE'};
    assert.equal(isTurn({...turn(1),cards:[card]}),true);
    assert.equal(isTurn({...turn(1),cards:[{...card,pageSize:21}]}),false);
    assert.equal(isTurn({...turn(1),cards:[{...card,orders:[{...item,totalPrice:-1}]}]}),false);
});

test('최종 동의 입력·접수 영수증을 구분하고 불완전한 접수 응답을 거부한다', () => {
    const { isTurn } = load('supportApi.ts');
    const { inputLabel } = load('supportState.ts');
    const receipt = { ...turn(30), input: { type: 'CONFIRM_CANCEL', sourceTurnId: 29, orderId: 101 }, cards: [{ type: 'CANCELLATION_REQUEST', order: { orderId: 101, title: '소파', createdAt: timestamp, orderStatus: 'CONFIRMED' }, requestId: 'request-one', acceptedAt: timestamp, status: 'ACCEPTED' }] };
    assert.equal(isTurn(receipt), true);
    assert.equal(inputLabel(receipt), '취소 요청');
    assert.equal(isTurn({ ...receipt, cards: [{ ...receipt.cards[0], requestId: '' }] }), false);
    assert.equal(isTurn({ ...receipt, cards: [{ ...receipt.cards[0], status: 'CANCELLED' }] }), false);
    assert.equal(isTurn({ ...receipt, input: { ...receipt.input, sourceTurnId: null } }), false);
});

test('취소 결과는 알려진 업무 상태와 요청별 식별자를 검증한다', () => {
    const { isCancellationOutcome } = load('supportApi.ts');
    const outcome = { requestId:'r1',orderId:101,status:'ACCEPTED',acceptedAt:timestamp,decidedAt:null,reasonCode:null };
    assert.equal(isCancellationOutcome(outcome),true);
    assert.equal(isCancellationOutcome({...outcome,status:'COMPLETED',decidedAt:timestamp,reasonCode:'CANCELLED'}),true);
    assert.equal(isCancellationOutcome({...outcome,status:'COMPLETED'}),false);
    assert.equal(isCancellationOutcome({...outcome,status:'UNKNOWN'}),false);
});
test('자동 결과 조회는 30초 이후와 최종 결과에서 멈춘다', () => {
    const { shouldPollCancellation } = load('supportState.ts');
    assert.equal(shouldPollCancellation('ACCEPTED',1000,30999),true);
    assert.equal(shouldPollCancellation('ACCEPTED',1000,31000),false);
    assert.equal(shouldPollCancellation('COMPLETED',1000,2000),false);
    assert.equal(shouldPollCancellation('REJECTED',1000,2000),false);
});
test('지연된 처리중 응답은 최종 결과를 덮어쓰지 않는다', () => {
    const { mergeCancellationOutcome } = load('supportState.ts');
    const complete = {requestId:'r1',orderId:101,status:'COMPLETED'};
    assert.equal(mergeCancellationOutcome(complete,{...complete,status:'ACCEPTED'}),complete);
    assert.equal(mergeCancellationOutcome(complete,{...complete,requestId:'other'}),complete);
});

 test('주문 선택 말풍선은 원본 목록 순번과 주문번호를 표시하고 원본이 없으면 번호를 유지한다', () => {
    const { inputLabel } = load('supportState.ts');
    const source = { turnId: 4, cards: [{ type: 'ORDERS', orders: [{ orderId: 10 }, { orderId: 20 }] }] };
    const selected = { input: { type: 'SELECT_ORDER', sourceTurnId: 4, orderId: 20 } };
    assert.equal(inputLabel(selected, [source]), '2번 주문(#20) 취소 가능 여부 확인');
    assert.equal(inputLabel(selected, []), '주문 #20 취소 가능 여부 확인');
 });

test('확인 카드의 맞아요와 일반 목록 행의 선택을 저장 턴 기준으로 구분한다', () => {
    const { inputLabel } = load('supportState.ts');
    const selected = { input: { type: 'SELECT_ORDER', sourceTurnId: 4, orderId: 20 } };
    const order = { orderId: 20 };
    const confirmation = { turnId: 4, cards: [{ type: 'ORDERS', confirmLatest: true, orders: [order] }] };
    const list = { turnId: 4, cards: [{ type: 'ORDERS', orders: [{ orderId: 10 }, order] }] };
    assert.equal(inputLabel(selected, [confirmation]), '맞아요');
    assert.equal(inputLabel(selected, [list]), '2번 주문(#20) 취소 가능 여부 확인');
});

test('일일 한도 429 문구를 그대로 표시하고 pending을 풀어 취소 버튼을 계속 처리한다', async () => {
    const message = '오늘 상담 한도를 모두 사용했어요. 버튼 선택과 이전 대화 기록은 계속 이용할 수 있어요.';
    const sent = [];
    const { supportApi } = load('supportApi.ts', { fetch: async (_url, options) => {
        const body = JSON.parse(options.body); sent.push(body.input);
        return body.input.type === 'TEXT'
            ? { status: 429, ok: false, json: async () => ({ code: 'AI_DAILY_LIMIT', message }) }
            : { status: 200, ok: true, json: async () => ({ ...turn(2), input: body.input }) };
    } });
    const React = require('react');
    const original = { ...turn(1), cards: [{ type: 'ELIGIBILITY', order: { orderId: 101, title: '책상' }, eligibility: 'ELIGIBLE', cancellationOffer: true }] };
    // Run the actual view callbacks with persistent hook state; no DOM or network is required.
    const states = [[], null, { id: 1 }, [original], null, '주문 문의', false, false, false, '', null, false];
    const refs = []; let stateIndex = 0, refIndex = 0;
    const hooks = { ...React,
        useState: initial => { const i = stateIndex++; if (!(i in states)) states[i] = initial; return [states[i], next => { states[i] = typeof next === 'function' ? next(states[i]) : next; }]; },
        useRef: initial => { const i = refIndex++; return refs[i] ??= { current: initial }; },
        useEffect: () => {}
    };
    const viewModule = { exports: {} };
    const source = readFileSync(path.join(__dirname, '../src/features/support/SupportScreen.tsx'), 'utf8');
    const output = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX } }).outputText;
    const helpers = load('supportState.ts');
    runInNewContext(output + '\nexports.ViewForTest = SupportView;', {
        module: viewModule, exports: viewModule.exports, Error, crypto: { randomUUID: () => 'synthetic-request' },
        require: name => {
            if (name === 'react') return hooks;
            if (name === 'react/jsx-runtime') return require(name);
            if (name === 'react-router') return { useNavigate: () => () => {} };
            if (name.endsWith('/http')) return { ApiError, tokenStore: { get: () => 'member' } };
            if (name.endsWith('/navigation')) return { useNavigateToLogin: () => () => {} };
            if (name.endsWith('/supportApi')) return { supportApi: { ...supportApi, detail: async () => ({ conversation: { id: 1 }, items: [original], activeTurn: null, nextCursor: null }) } };
            if (name.endsWith('/supportState')) return helpers;
            if (name.endsWith('.css')) return { default: {} };
            return {};
        }
    });
    const render = () => { stateIndex = 0; refIndex = 0; return viewModule.exports.ViewForTest({ conversationId: 1 }); };
    function nodes(node) { return !node || typeof node !== 'object' ? [] : Array.isArray(node) ? node.flatMap(nodes) : [node, ...nodes(node.props?.children)]; }
    let tree = render();
    const example = nodes(tree).find(n => n.type === 'textarea');
    example.props.onChange({ target: { value: '주문 문의' } });
    tree = render();
    nodes(tree).find(n => n.type === 'form').props.onSubmit({ preventDefault() {} });
    // The form intentionally discards the promise, so drain its async API steps.
    await new Promise(resolve => setImmediate(resolve));
    tree = render();
    assert.equal(nodes(tree).find(n => n.props?.role === 'alert').props.children, message);
    assert.equal(nodes(tree).find(n => n.type === 'textarea').props.value, '주문 문의');
    for (const [handler, type] of [['onCancel', 'CONFIRM_CANCEL'], ['onOther', 'DECLINE'], ['onSelect', 'SELECT_ORDER'], ['onNext', 'NEXT_ORDERS']]) {
        const card = nodes(tree).find(n => n.props?.card === original.cards[0]);
        assert.equal(card.props.disabled, false);
        card.props[handler](101);
        await new Promise(resolve => setImmediate(resolve));
        assert.equal(sent.at(-1).type, type);
        tree = render();
    }
});
