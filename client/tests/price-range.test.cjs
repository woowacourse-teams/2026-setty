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
const furniture = load('model/furniture.ts', {});
const { PriceFilter } = load('features/home/priceRange.ts', { '../../model/furniture': furniture });
test('가격 칩 문구는 만·억 단위로 줄이고 범위의 앞쪽 단위는 뺀다', () => {
    assert.equal(PriceFilter.label({ min: null, max: 50000 }), '5만 원 이하');
    assert.equal(PriceFilter.label({ min: 50000, max: null }), '5만 원 이상');
    assert.equal(PriceFilter.label({ min: 50000, max: 200000 }), '5만~20만 원');
    assert.equal(PriceFilter.label({ min: 5000, max: 200000 }), '5,000~20만 원');
    assert.equal(PriceFilter.label({ min: null, max: 100000000 }), '1억 원 이하');
    assert.equal(PriceFilter.label({ min: 15000, max: 123456789 }), '1.5만~123,456,789원');
});
