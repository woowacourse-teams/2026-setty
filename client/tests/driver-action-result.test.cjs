const assert = require('node:assert/strict');
const { test } = require('node:test');
const { readFileSync } = require('node:fs');
const { join } = require('node:path');
const ts = require('typescript');

const source = readFileSync(join(__dirname, '../src/features/driver/actionResult.ts'), 'utf8');
const { outputText } = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } });
const actionResult = {};
new Function('exports', outputText)(actionResult);
const { withMinimumDuration } = actionResult;

function controlledWait() {
    let release;
    const waited = [];
    const wait = (millis) => {
        waited.push(millis);
        return new Promise((resolve) => { release = resolve; });
    };
    return { wait, waited, release: () => release() };
}

test('응답이 먼저 와도 최소 시간이 지나야 결과를 돌려준다', async () => {
    const timer = controlledWait();
    let settled = false;
    const result = withMinimumDuration(Promise.resolve('ok'), 1000, timer.wait).then((value) => {
        settled = true;
        return value;
    });
    await new Promise((resolve) => setImmediate(resolve));
    assert.equal(settled, false);
    assert.deepEqual(timer.waited, [1000]);
    timer.release();
    assert.equal(await result, 'ok');
});

test('실패하면 최소 시간을 기다리지 않고 바로 던진다', async () => {
    const timer = controlledWait();
    const failure = new Error('409');
    await assert.rejects(withMinimumDuration(Promise.reject(failure), 1000, timer.wait), failure);
});
