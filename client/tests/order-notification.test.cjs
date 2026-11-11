const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const path = require('node:path');
const { test } = require('node:test');
const { runInNewContext } = require('node:vm');
const ts = require('typescript');
function load(file, dependencies) {
    const source = readFileSync(path.join(__dirname, '../src', file), 'utf8');
    const { outputText } = ts.transpileModule(source, { compilerOptions: {
        module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020
    } });
    const module = { exports: {} };
    runInNewContext(outputText, { module, exports: module.exports, Intl, require: name => {
        assert.ok(Object.hasOwn(dependencies, name), name); return dependencies[name];
    } });
    return module.exports;
}
const { driverNoticeOf, notificationPath, notificationTypeOf, orderNoticeOf } = load('model/notification.ts', {});
test('주문 알림은 구매자면 주문 상세, 판매자면 매물 상세로 간다', () => {
    assert.equal(notificationPath(71, orderNoticeOf(7001, 'BUYER', '기사가 배정됐어요')), '/me/orders/7001');
    assert.equal(notificationPath(71, orderNoticeOf(7001, 'SELLER', '내 물건이 팔렸어요')), '/listings/71');
    assert.equal(notificationPath(71, null), '/listings/71');
});
test('주문 알림 필드가 빠지거나 받는 사람을 모르면 주문 알림으로 보지 않는다', () => {
    assert.equal(orderNoticeOf(null, 'BUYER', '기사가 배정됐어요'), null);
    assert.equal(orderNoticeOf(7001, 'DRIVER', '기사가 배정됐어요'), null);
    assert.equal(orderNoticeOf(7001, 'BUYER', ''), null);
    assert.equal(orderNoticeOf(Number.NaN, 'BUYER', '기사가 배정됐어요'), null);
});
test('정산 확정 알림만 금액을 들고 다니고 나머지는 null이다', () => {
    assert.equal(orderNoticeOf(7001, 'SELLER', '정산이 확정됐어요', 150000).amount, 150000);
    assert.equal(orderNoticeOf(7001, 'SELLER', '내 물건이 팔렸어요', null).amount, null);
    assert.equal(orderNoticeOf(7001, 'SELLER', '내 물건이 팔렸어요').amount, null);
});
test('알림 종류는 KEYWORD·ORDER 말고는 UNKNOWN이다', () => {
    assert.equal(notificationTypeOf('ORDER'), 'ORDER');
    assert.equal(notificationTypeOf('KEYWORD'), 'KEYWORD');
    assert.equal(notificationTypeOf('PRICE'), 'UNKNOWN');
    assert.equal(notificationTypeOf(null), 'UNKNOWN');
});
test('기사 푸시는 type이 DRIVER이고 제목이 있을 때만 기사 내 정보로 간다', () => {
    const notice = driverNoticeOf({ type: 'DRIVER', headline: '배송비 정산이 확정됐어요', body: '15,000원' });
    assert.equal(notice.headline, '배송비 정산이 확정됐어요');
    assert.equal(notice.body, '15,000원');
    assert.equal(notice.path, '/driver/me');
    assert.equal(driverNoticeOf({ type: 'ORDER', headline: '배송비 정산이 확정됐어요' }), null);
    assert.equal(driverNoticeOf({ type: 'DRIVER' }), null);
});
