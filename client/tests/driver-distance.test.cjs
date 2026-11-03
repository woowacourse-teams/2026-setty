const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const path = require('node:path');
const { test } = require('node:test');
const { runInNewContext } = require('node:vm');
const ts = require('typescript');

const source = readFileSync(path.join(__dirname, '../src/model/delivery.ts'), 'utf8');
const { outputText } = ts.transpileModule(source, { compilerOptions: {
    module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020
} });
const model = { exports: {} };
runInNewContext(outputText, { module: model, exports: model.exports, require: name => {
    assert.equal(name, './furniture');
    return { furnitureCategories: [], categoryLabel: value => value };
} });

test('예상 거리는 미터를 킬로미터로 변환해 소수 첫째 자리까지 표시한다', () => {
    const format = model.exports.formatEstimatedDistance;
    assert.equal(format(24052), '예상 거리 24.1km (지역 기준)');
    assert.equal(format(1000), '예상 거리 1km (지역 기준)');
    assert.equal(format(173), '예상 거리 0.2km (지역 기준)');
});

test('거리 정보가 없을 때는 0km로 표시하지 않는다', () => {
    const format = model.exports.formatEstimatedDistance;
    assert.equal(format(null), '거리 정보 없음');
    assert.equal(format(undefined), '거리 정보 없음');
    assert.equal(format(0), '예상 거리 0km (지역 기준)');
});
