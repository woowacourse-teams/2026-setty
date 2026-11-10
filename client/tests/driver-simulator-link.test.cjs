const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const path = require('node:path');
const { test } = require('node:test');
const { runInNewContext } = require('node:vm');
const ts = require('typescript');

const load = (file, context) => {
    const { outputText } = ts.transpileModule(readFileSync(path.join(__dirname, '..', file), 'utf8'), { compilerOptions: {
        module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020
    } });
    const module = { exports: {} };
    runInNewContext(outputText, { ...context, module, exports: module.exports });
    return module.exports;
};

function browser(initialHash) {
    const listeners = {};
    const state = { token: '', simulator: false, reloads: 0, replaced: [] };
    const window = {
        location: { pathname: '/driver', search: '', hash: initialHash, reload: () => { state.reloads += 1; } },
        history: {
            state: { key: 'router-key' },
            replaceState: (data, _title, url) => {
                state.replaced.push({ data, url });
                window.location.hash = '';
            }
        },
        addEventListener: (name, listener) => { listeners[name] = listener; }
    };
    const driverLocation = load('src/model/driverLocation.ts', { URLSearchParams });
    const http = {
        deliveryTokenStore: { set: (token) => { state.token = token; } },
        deliverySimulatorStore: { set: () => { state.simulator = true; } }
    };
    const link = load('src/features/driver/simulatorLink.ts', {
        window,
        require: (name) => (name.endsWith('/http') ? http : driverLocation)
    });
    const openHash = (hash) => {
        window.location.hash = hash;
        listeners.hashchange();
    };
    return { link, state, openHash };
}

test('처음 열 때 조각의 토큰을 저장하고 라우터 상태를 지키며 조각을 지운다', () => {
    const { link, state } = browser('#token=first&sim=1');

    link.listenSimulatorLinks();

    assert.equal(state.token, 'first');
    assert.equal(state.simulator, true);
    assert.deepEqual(state.replaced.map(({ data, url }) => ({ key: data.key, url })), [{ key: 'router-key', url: '/driver' }]);
    assert.equal(state.reloads, 0);
});

test('이미 열린 탭에서 조각만 바뀌면 새 토큰을 저장하고 화면을 다시 연다', () => {
    const { link, state, openHash } = browser('');
    link.listenSimulatorLinks();

    openHash('#token=second&sim=1');

    assert.equal(state.token, 'second');
    assert.equal(state.simulator, true);
    assert.equal(state.reloads, 1);
});

test('토큰이 없는 조각으로 바뀌면 아무것도 하지 않는다', () => {
    const { link, state, openHash } = browser('');
    link.listenSimulatorLinks();

    openHash('#section');

    assert.equal(state.token, '');
    assert.equal(state.reloads, 0);
    assert.equal(state.replaced.length, 0);
});
