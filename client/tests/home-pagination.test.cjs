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
    runInNewContext(outputText, { module, exports: module.exports, require: name => {
        assert.ok(Object.hasOwn(dependencies, name), name); return dependencies[name];
    } });
    return module.exports;
}
const settle = () => new Promise(setImmediate);
// vm 안에서 만든 배열은 바깥 Array와 프로토타입이 달라 deepEqual이 실패하므로 평범한 값으로 바꿔 비교한다
const plain = value => JSON.parse(JSON.stringify(value));
const page = (ids, nextCursor = null) => ({ items: ids.map(id => ({ id })), nextCursor });
const ids = store => Array.from(store.useHomeState().furnitures, item => item.id);
function home() {
    const requests = [], messages = [];
    const store = load('features/home/homeStore.ts', {
        react: { useSyncExternalStore: (_, snapshot) => snapshot() },
        '../../api/errors': { isMemberDongRequired: error => error === 'DONG' },
        '../../api/http': { tokenStore: { get: () => 'token' } },
        '../../model/furniture': { conditionGrades: ['S', 'A', 'B', 'C'] },
        './priceRange': { PriceFilter: { equals: (a, b) => a?.min === b?.min && a?.max === b?.max } },
        '../../api/repositories': {
            furnitureRepository: { getFurniturePage: params => new Promise((resolve, reject) => requests.push({ params, resolve, reject })) },
            userRepository: { getMe: async () => ({ loginId: 'tester' }) }
        },
        '../../ui/snackbar': { showSnackbar: message => messages.push(message) }
    });
    return { store, requests, messages };
}
test('기본 목록도 다음 커서를 쓰고 중복 요청과 중복 항목을 막는다', async () => {
    const { store, requests } = home();
    store.loadListings(); requests[0].resolve(page([3, 2], 'cursor-2')); await settle();
    assert.equal(store.hasMore(store.useHomeState()), true);
    store.loadMore(); store.loadMore();
    assert.equal(requests.length, 2); assert.equal(requests[1].params.cursor, 'cursor-2');
    requests[1].resolve(page([2, 1])); await settle();
    assert.deepEqual(ids(store), [3, 2, 1]); assert.equal(store.hasMore(store.useHomeState()), false);
});
test('필터 변경은 교집합 조건을 보내고 첫 페이지부터 받는다', async () => {
    const { store, requests } = home();
    store.selectCategory('DESK'); requests[0].resolve(page([5], 'old')); await settle();
    store.selectMaxDeliveryFee(20000);
    assert.deepEqual(plain(requests[1].params), { category: 'DESK', conditionGrades: [], minPrice: null, maxPrice: null, maxDeliveryFee: 20000 });
    requests[1].resolve(page([1])); await settle(); assert.deepEqual(ids(store), [1]);
    store.selectCategory(null);
    assert.deepEqual(plain(requests[2].params), { category: null, conditionGrades: [], minPrice: null, maxPrice: null, maxDeliveryFee: 20000 });
});
test('가격 범위는 최소·최대로 보내고 같은 범위는 다시 요청하지 않으며 실패하면 이전 범위로 돌아간다', async () => {
    const { store, requests } = home();
    store.loadListings(); requests[0].resolve(page([3], 'next')); await settle();
    store.selectPriceRange({ min: null, max: 100000 });
    assert.equal(requests[1].params.minPrice, null); assert.equal(requests[1].params.maxPrice, 100000);
    requests[1].resolve(page([1])); await settle();
    store.selectPriceRange({ min: null, max: 100000 }); assert.equal(requests.length, 2);
    store.selectPriceRange({ min: 50000, max: 200000 }); requests[2].reject(new Error()); await settle();
    assert.deepEqual(plain(store.useHomeState().priceRange), { min: null, max: 100000 });
    store.loadMore(); assert.equal(requests.length, 3);
});
test('등급은 좋은 등급부터 보내고 같은 묶음은 다시 받지 않으며 실패하면 이전 등급으로 돌아간다', async () => {
    const { store, requests } = home();
    store.loadListings(); requests[0].resolve(page([3], 'next')); await settle();
    store.selectGrades(['B', 'S']);
    assert.deepEqual(plain(requests[1].params.conditionGrades), ['S', 'B']);
    requests[1].resolve(page([1])); await settle();
    store.selectGrades(['S', 'B']); assert.equal(requests.length, 2);
    store.selectGrades(['S']); assert.deepEqual(plain(requests[2].params.conditionGrades), ['S']);
    requests[2].reject(new Error()); await settle();
    assert.deepEqual(plain(store.useHomeState().selectedGrades), ['S', 'B']);
});
test('초기화는 등급·가격·배송비를 한 번에 풀고 카테고리는 남기며 풀 것이 없으면 요청하지 않는다', async () => {
    const { store, requests } = home();
    store.selectCategory('DESK'); requests[0].resolve(page([3])); await settle();
    store.clearFilters(); assert.equal(requests.length, 1);
    store.selectGrades(['A']); requests[1].resolve(page([2])); await settle();
    store.selectPriceRange({ min: null, max: 50000 }); requests[2].resolve(page([1])); await settle();
    store.clearFilters();
    assert.deepEqual(plain(requests[3].params), { category: 'DESK', conditionGrades: [], minPrice: null, maxPrice: null, maxDeliveryFee: null });
});
test('이전 페이지의 늦은 성공과 실패가 새 조건의 결과를 덮지 않는다', async () => {
    for (const fail of [false, true]) {
        const { store, requests } = home();
        store.loadListings(); requests[0].resolve(page([3], 'old')); await settle();
        store.loadMore(); store.selectCategory('DESK'); requests[2].resolve(page([8])); await settle();
        if (fail) requests[1].reject(new Error()); else requests[1].resolve(page([2], 'stale'));
        await settle(); assert.deepEqual(ids(store), [8]); assert.equal(store.useHomeState().nextCursor, null);
    }
});
test('조건 변경 실패는 이전 목록과 조건 및 커서를 함께 복구한다', async () => {
    const { store, requests } = home();
    store.loadListings(); requests[0].resolve(page([3], 'next')); await settle();
    store.selectCategory('DESK'); requests[1].reject(new Error()); await settle();
    assert.equal(store.useHomeState().selectedCategory, null);
    assert.equal(store.useHomeState().nextCursor, 'next'); assert.deepEqual(ids(store), [3]);
    store.loadMore(); assert.equal(requests[2].params.category, null);
});
test('상세 복귀 시 이미 읽은 범위를 갱신하며 뒤쪽 목록을 유지한다', async () => {
    const { store, requests } = home();
    store.loadListings(); requests[0].resolve(page([4, 3], 'a')); await settle();
    store.loadMore(); requests[1].resolve(page([2, 1], 'b')); await settle();
    store.enterHome(() => {}); assert.deepEqual(ids(store), [4, 3, 2, 1]);
    requests[2].resolve(page([5, 4], 'c')); await settle(); assert.equal(requests[3].params.cursor, 'c');
    requests[3].resolve(page([3, 2], 'd')); await settle();
    assert.deepEqual(ids(store), [5, 4, 3, 2]); assert.equal(store.useHomeState().nextCursor, 'd');
});
test('주소 오류는 목록을 유지하며 안내하고 계정 초기화는 늦은 응답을 버린다', async () => {
    const { store, requests } = home(); let asked = 0;
    store.enterHome(() => asked++); requests[0].resolve(page([1], 'next')); await settle();
    store.selectMaxDeliveryFee(10000); requests[1].reject('DONG'); await settle();
    assert.equal(asked, 1); assert.equal(store.useHomeState().maxDeliveryFee, null);
    store.loadMore(); requests[2].reject(new Error()); await settle();
    store.loadMore(); assert.equal(requests[3].params.cursor, 'next');
    store.resetHomeStore(); requests[3].resolve(page([2])); await settle(); assert.deepEqual(ids(store), []);
});
test('repository는 size=20과 조건을 보내고 찜 수와 거래 상태를 보존한다', async () => {
    const requests = [];
    const { furnitureRepository } = load('api/repositories.ts', {
        '../model/dong': { dongDisplayName: () => '동' },
        '../model/furniture': { furnitureCategoryOf: value => value, orderStateOf: value => value },
        '../model/keyword': {},
        '../model/notification': {},
        '../model/order': {},
        './http': { apiCall: async (url, options) => { requests.push({ url, options }); return {
            items: [{ id: 1, category: 'DESK', favoriteCount: 3, orderState: 'TRADING', totalPrice: 300 }], nextCursor: 'next'
        }; } }
    });
    const result = await furnitureRepository.getFurniturePage({ category: 'DESK', conditionGrades: ['S', 'A'], minPrice: 1000, query: '%_', maxDeliveryFee: 20000, cursor: 'old' });
    assert.deepEqual({ ...requests[0].options.query }, { category: 'DESK', conditionGrade: 'S,A', minPrice: 1000, maxPrice: null, query: '%_', maxDeliveryFee: 20000, cursor: 'old', size: 20 });
    assert.equal(result.nextCursor, 'next'); assert.equal(result.items[0].favoriteCount, 3);
    assert.equal(result.items[0].orderState, 'TRADING'); assert.equal(result.items[0].totalPrice, 300);
});
