const assert = require('node:assert/strict');
const { test } = require('node:test');
const { readFileSync } = require('node:fs');
const { runInNewContext } = require('node:vm');
const ts = require('typescript');
const React = require('react');
const { renderToStaticMarkup } = require('react-dom/server');
const source = readFileSync(require('node:path').join(__dirname, '../src/features/support/SupportScreen.tsx'), 'utf8');
const output = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX } }).outputText;
const moduleStub = { exports: {} };
runInNewContext(output + '\nexports.CardForTest = Card;', {
    module: moduleStub, exports: moduleStub.exports,
    require(name) {
        if (name === 'react' || name === 'react/jsx-runtime') return require(name);
        if (name.endsWith('.css')) return { default: new Proxy({}, { get: (_, key) => key }) };
        if (name === 'react-router') return { Link: ({ children, 'aria-label': label }) => React.createElement('a', { 'aria-label': label }, children) };
        if (name.endsWith('/components')) return { FurnitureThumbnail: ({alt}) => React.createElement('span', {}, alt), StatusBadge: ({text}) => React.createElement('span', {}, text), Button: ({ children, disabled }) => React.createElement('button', { disabled }, children) };
        if (name.endsWith('/navigation')) return { routes: { orderDetail: id => '/me/orders/' + id } };
        if (name.endsWith('/furniture')) return { formatWon: value => `${value}원` };
        if (name.endsWith('/Icon')) return { Icon: ({name}) => React.createElement('i', {}, name) };
        if (name.endsWith('/order')) return { orderStatusOrNull: value => value === 'CONFIRMED' ? value : null, orderStatusLabel: () => '주문 확정', deliveryStatusOf: value => value };
        return {};
    }
});
const card = {
    type: 'ELIGIBILITY', order: { orderId: 101, title: '책상' }, eligibility: 'ELIGIBLE',
    reasonCode: 'BEFORE_DRIVER_ACCEPTANCE', observedAt: new Date().toISOString(),
    cancellationOffer: true, confirmationExpiresAt: new Date(Date.now() + 300000).toISOString()
};
function render(overrides = {}, current = true) {
    return renderToStaticMarkup(React.createElement(moduleStub.exports.CardForTest, { card: { ...card, ...overrides }, current, latest: current, disabled: false }));
}
test('추가 질문의 확인 카드는 전체 상태·시각을 반복하지 않고 주문과 최종 버튼만 표시한다', () => {
    const html = render({ confirmationOnly: true });
    assert.match(html, /책상/);
    assert.match(html, /취소 요청<\/button>/);
    assert.doesNotMatch(html, /확인 시각|현재 취소 요청 가능|배차 수락 전/);
});

test('확인 카드에서 테스트 서비스 고지를 반복하지 않는다', () => {
    assert.doesNotMatch(render(), /테스트 서비스/);
});
test('PAID 주문은 확정 대기를 안내하고 취소 요청 버튼을 표시하지 않는다', () => {
    const html = render({
        eligibility: 'INELIGIBLE',
        reasonCode: 'ORDER_CONFIRMATION_PENDING',
        cancellationOffer: false,
    });
    assert.match(html, /결제가 승인되어 주문 확정을 기다리고 있어요/);
    assert.doesNotMatch(html, /취소 요청<\/button>/);
});
test('과거 턴에는 재확인 버튼이 없고 마지막 만료 턴에만 한 개 있다', () => {
    const expired = { confirmationExpiresAt: '2020-01-01T00:00:00Z' };
    const old = render(expired, false);
    const latest = render(expired, true);
    assert.doesNotMatch(old, /취소 가능 여부 다시 확인/);
    assert.equal((latest.match(/취소 가능 여부 다시 확인/g) || []).length, 1);
});

test('가능 여부 카드의 주문명은 한 번만 표시되고 과거 카드에만 시점 안내를 표시한다', () => {
    const current = render();
    assert.equal((current.match(/책상/g) || []).length, 1);
    assert.doesNotMatch(current, /당시 조회 결과예요/);
    assert.match(render({}, false), /당시 조회 결과예요/);
    assert.match(current, /class="eligibility eligible"/);
});

test('날짜 검색에서 제외된 날짜 미상 주문 수를 표시하고 과거 카드도 읽는다', () => {
    const orders = { type: 'ORDERS', orders: [], nextCursor: null, hasUndatedOrders: true };
    assert.match(render({ ...orders, excludedUndatedCount: 2 }), /주문일 정보가 없는 주문 2건은 날짜 검색에서 제외/);
    assert.match(render(orders), /주문일이 없는 주문은 날짜 검색에 포함되지/);
});

test('과거 취소 업무 카드는 이전 접수 결과와 접수 시각을 표시한다', () => {
    const request = { type: 'CANCELLATION_REQUEST', order: { orderId: 101, title: '책상' }, requestId: 'old', acceptedAt: '2026-11-01T00:00:00Z', status: 'ACCEPTED' };
    const old = render({ ...request, historical: true, previousStatus: 'COMPLETED' });
    assert.match(old, /이전에 접수한 취소 요청 · 완료/);
    assert.match(old, /접수 시각/);
    assert.doesNotMatch(old, /취소가 완료됐어요/);
    assert.match(render(request), /취소 처리 중이에요/);
});

test('취소된 주문은 선택 버튼 없이 상세 링크만 표시한다', () => {
    const html = render({ type: 'ORDERS', orders: [{ orderId: 102, title: '취소된 의자', orderStatus: 'CANCELLED' }], nextCursor: null });
    assert.match(html, /취소된 의자 주문 102 상세 보기/);
    assert.doesNotMatch(html, /취소 가능 여부 확인|맞아요/);
});
test('일반 주문 행은 버튼이며 확인 카드의 두 버튼과 상세 링크는 별도다', () => {
    const order = { orderId: 101, title: '책상', orderStatus: 'CONFIRMED' };
    const html = render({ type: 'ORDERS', orders: [order], confirmLatest: true });
    assert.match(html, /<button[^>]+aria-label="책상 주문 101 맞아요"/);
    assert.doesNotMatch(html, /1\. 책상/);
    assert.match(html, /다른 주문이에요/);
    const list = render({ type: 'ORDERS', orders: [order, { ...order, orderId: 102 }] });
    assert.match(list, /1\. 책상/);
    assert.match(list, /2\. 책상/);
    assert.match(html, /책상 주문 101 상세 보기/);
});

const frozenTime = '2026-11-01T00:00:00Z';
const frozenOrder = { orderId: 101, title: '책상', createdAt: frozenTime, createdDateKst: '2026-11-01', orderStatus: 'CONFIRMED', totalPrice: 120000 };
const renderContracts = [
    ['orders', { type: 'ORDERS', orders: [frozenOrder], nextCursor: 100, pageSize: 6, purpose: 'BROWSE' }],
    ['order-confirmation', { type: 'ORDERS', orders: [frozenOrder], confirmLatest: true, purpose: 'CANCEL_CHECK' }],
    ['eligibility', { ...card, observedAt: frozenTime, cancellationOffer: false }],
    ['consent-expired', { ...card, observedAt: frozenTime, confirmationExpiresAt: frozenTime }],
    ['confirmation-only', { ...card, confirmationOnly: true, confirmationExpiresAt: '2099-01-01T00:00:00Z' }],
    ...[false, true].flatMap(historical => ['ACCEPTED', 'COMPLETED', 'REJECTED'].map(previousStatus =>
        [`receipt-${historical}-${previousStatus}`, { type: 'CANCELLATION_REQUEST', order: frozenOrder, acceptedAt: frozenTime, requestId: 'r', status: 'ACCEPTED', historical, previousStatus }]))
];
for (const [name, value] of renderContracts) test(`렌더링 계약: ${name}`, () => {
    const html = render(value);
    const file = require('node:path').join(__dirname, 'fixtures', `support-${name}.html`);
    assert.equal(html + '\n', readFileSync(file, 'utf8'));
});
