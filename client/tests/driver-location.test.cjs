const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const path = require('node:path');
const { test } = require('node:test');
const { runInNewContext } = require('node:vm');
const ts = require('typescript');

const source = readFileSync(path.join(__dirname, '../src/model/driverLocation.ts'), 'utf8');
const { outputText } = ts.transpileModule(source, { compilerOptions: {
    module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020
} });
const model = { exports: {} };
runInNewContext(outputText, { module: model, exports: model.exports, URLSearchParams });

test('서버 GridCell과 같은 250m 칸 번호를 계산한다', () => {
    // 서버 GridCellTest와 같은 기대값(~/osrm/phase8/grid_acc.py 식)
    assert.equal(model.exports.gridCellOf(37.4985, 126.9520), 1468580097);
    assert.equal(model.exports.gridCellOf(37.5665, 126.9780), 44826 * 32768 + 16672);
});

test('좌표가 아니거나 격자 밖이면 칸이 없다', () => {
    assert.equal(model.exports.gridCellOf(Number.NaN, 126.9), null);
    assert.equal(model.exports.gridCellOf(-33.8, 151.2), null);
});

test('시뮬레이터 링크의 토큰과 시뮬레이터 여부를 읽는다', () => {
    assert.deepEqual({ ...model.exports.parseSimulatorLink('#token=abc-123&sim=1') }, { token: 'abc-123', simulator: true });
    assert.deepEqual({ ...model.exports.parseSimulatorLink('#token=abc-123') }, { token: 'abc-123', simulator: false });
    assert.equal(model.exports.parseSimulatorLink(''), null);
    assert.equal(model.exports.parseSimulatorLink('#sim=1'), null);
});
