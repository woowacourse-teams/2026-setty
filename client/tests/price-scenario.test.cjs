const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const path = require('node:path');
const { test } = require('node:test');
const { runInNewContext } = require('node:vm');
const ts = require('typescript');

function loadModule(file, globals, dependencies = {}) {
    const source = readFileSync(path.join(__dirname, '../src/analytics', file), 'utf8');
    const { outputText } = ts.transpileModule(source, {
        compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020, esModuleInterop: true }
    });
    const module = { exports: {} };
    runInNewContext(outputText, {
        ...globals,
        module,
        exports: module.exports,
        require(name) {
            assert.ok(Object.hasOwn(dependencies, name), `Unexpected dependency: ${name}`);
            return dependencies[name];
        }
    }, { filename: file });
    return module.exports;
}

function loadScenario({ enabled = true, raw = null, blockedStorage = false } = {}) {
    const storage = new Map([['setty:price-scenario', raw]]);
    const scenario = loadModule('priceScenario.ts', {
        __ENABLE_PRICE_SCENARIO__: enabled,
        window: { sessionStorage: { getItem(key) {
            if (blockedStorage) throw new Error('Storage access denied');
            return storage.get(key) ?? null;
        } } }
    });
    return { scenario, storage };
}

const configuration = (variant, runId = 'price-run-001') => JSON.stringify({ price_variant: variant, scenario_run_id: runId });

test('일반 빌드는 A 설정이 있어도 기존 B 화면을 사용하고 실험 속성을 붙이지 않는다', () => {
    const { scenario } = loadScenario({ enabled: false, raw: configuration('A') });
    assert.equal(scenario.getPriceVariant(), 'B');
    assert.deepEqual({ ...scenario.getPriceScenarioEventProperties() }, {});
});

test('유효한 A/B 조건은 실행 ID와 가상 데이터 표시를 함께 유지한다', () => {
    for (const variant of ['A', 'B']) {
        const { scenario } = loadScenario({ raw: configuration(variant, '  price-run-001  ') });
        assert.equal(scenario.getPriceVariant(), variant);
        assert.deepEqual({ ...scenario.getPriceScenarioEventProperties() }, {
            price_variant: variant, scenario_run_id: 'price-run-001', is_synthetic: true
        });
    }
});

test('누락되거나 잘못된 설정은 B로 복귀하며 부분적인 실험 데이터로 분류하지 않는다', () => {
    const invalid = [null, '', '{', 'null', '[]', '{}', configuration('C'), configuration('a'),
        configuration('A', ''), configuration('A', '  '), configuration('A', 123),
        JSON.stringify({ price_variant: 'A' })];
    for (const raw of invalid) {
        const { scenario } = loadScenario({ raw });
        assert.equal(scenario.getPriceVariant(), 'B', String(raw));
        assert.deepEqual({ ...scenario.getPriceScenarioEventProperties() }, {}, String(raw));
    }
});

test('저장소 접근이 차단되어도 화면 로딩이 실패하지 않는다', () => {
    const { scenario } = loadScenario({ blockedStorage: true });
    assert.equal(scenario.getPriceVariant(), 'B');
    assert.deepEqual({ ...scenario.getPriceScenarioEventProperties() }, {});
});

test('문서 실행 중 저장소가 바뀌어도 화면 조건과 이벤트 조건이 어긋나지 않는다', () => {
    const { scenario, storage } = loadScenario({ raw: configuration('A') });
    storage.set('setty:price-scenario', configuration('B', 'other-run'));
    assert.equal(scenario.getPriceVariant(), 'A');
    assert.deepEqual({ ...scenario.getPriceScenarioEventProperties() }, {
        price_variant: 'A', scenario_run_id: 'price-run-001', is_synthetic: true
    });
});

function analyticsWith(scenario) {
    const captures = [];
    const sdk = { init() {}, capture: (name, properties) => captures.push({ name, properties }) };
    const analytics = loadModule('posthog.ts', {
        __ENABLE_MSW__: false,
        __POSTHOG_KEY__: 'phc_fixturekey',
        __POSTHOG_HOST__: 'http://127.0.0.1'
    }, { 'posthog-js': sdk, './priceScenario': scenario });
    return { analytics, captures };
}

test('조회·결제창·결제 완료 이벤트는 실제 화면 조건을 유지하고 호출부의 잘못된 속성으로 덮이지 않는다', () => {
    const { scenario } = loadScenario({ raw: configuration('A') });
    const { analytics, captures } = analyticsWith(scenario);
    for (const event of ['product_viewed', 'checkout_opened', 'payment_succeeded']) {
        analytics.trackEvent(event, {
            listing_id: 11, price_variant: 'B', scenario_run_id: 'wrong-run', is_synthetic: false
        });
    }
    assert.equal(captures.length, 3);
    for (const { properties } of captures) {
        assert.deepEqual({ ...properties }, {
            listing_id: 11, price_variant: 'A', scenario_run_id: 'price-run-001', is_synthetic: true
        });
    }
});

test('일반 사용자 이벤트에는 시나리오 속성이 섞이지 않는다', () => {
    const { scenario } = loadScenario({ enabled: false, raw: configuration('A') });
    const { analytics, captures } = analyticsWith(scenario);
    analytics.trackEvent('checkout_opened', { listing_id: 11, amount: 160000 });
    assert.deepEqual({ ...captures[0].properties }, { listing_id: 11, amount: 160000 });
});
