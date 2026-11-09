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
const { canCancel, canConfirmPurchase, orderStatusLabel, orderStatusOrNull } = load('model/order.ts', {});
const order = (orderStatus, deliveryStatus) => ({ id: 1, listing: null, orderStatus, deliveryStatus });
test('배송이 끝난 주문은 취소 대신 구매를 확정한다', () => {
    assert.equal(canCancel(order('CONFIRMED', 'PICKED_UP')), true);
    assert.equal(canConfirmPurchase(order('CONFIRMED', 'PICKED_UP')), false);
    assert.equal(canCancel(order('CONFIRMED', 'DELIVERED')), false);
    assert.equal(canConfirmPurchase(order('CONFIRMED', 'DELIVERED')), true);
});
test('확정한 주문은 구매 확정으로 보이고 다시 확정하거나 취소할 수 없다', () => {
    const completed = order(orderStatusOrNull('COMPLETED'), 'DELIVERED');
    assert.equal(orderStatusLabel(completed), '구매 확정');
    assert.equal(canConfirmPurchase(completed), false);
    assert.equal(canCancel(completed), false);
});
