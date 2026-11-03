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
        assert.ok(Object.hasOwn(dependencies, name), name);
        return dependencies[name];
    } });
    return module.exports;
}

const settle = () => new Promise(setImmediate);
const cursor = (id, micros = '1793491200123456') => `${micros}_${id}`;
const page = (ids, nextCursor = null) => ({ items: ids.map(deliveryId => ({ deliveryId })), nextCursor });
const ids = store => Array.from(store.getSnapshot().state.data, item => item.deliveryId);

function driver() {
    const requests = [], messages = [];
    let token = 'driver-1', logins = 0;
    const fetchPage = (cursor, group = 'REQUESTED') => new Promise((resolve, reject) => requests.push({ cursor, group, resolve, reject }));
    const stores = load('features/driver/deliveryPageStore.ts', {
        '../../api/errors': {
            isUnauthorized: error => error === 'AUTH',
            toUserMessage: () => '조회 실패',
            toLoadError: error => ({ status: 'error', message: '조회 실패', isUnauthorized: error === 'AUTH' })
        },
        '../../api/deliveryRepository': { deliveryRepository: {
            getRequestPage: cursor => fetchPage(cursor),
            getShipmentPage: (group, cursor) => fetchPage(cursor, group)
        } },
        '../../api/http': { deliveryTokenStore: { get: () => token } },
        '../../ui/snackbar': { showSnackbar: message => messages.push(message) }
    });
    return { ...stores, requests, messages, enter: store => store.enter(() => logins++),
        logins: () => logins, changeAccount: value => { token = value; } };
}

test('다음 페이지를 한 번만 요청하고 중복 항목을 제거하며 마지막 페이지에서 멈춘다', async () => {
    const context = driver(), store = context.requestPageStore;
    context.enter(store); context.requests[0].resolve(page([3, 2], cursor(2))); await settle();
    store.loadMore(); store.loadMore();
    assert.equal(context.requests.length, 2);
    assert.equal(context.requests[1].cursor, cursor(2));
    context.requests[1].resolve(page([2, 1, 1])); await settle();
    assert.deepEqual(ids(store), [3, 2, 1]);
    store.loadMore(); assert.equal(context.requests.length, 2);
});

test('실시간 갱신은 이미 읽은 분량을 모두 받은 뒤 목록과 커서를 함께 교체한다', async () => {
    const context = driver(), store = context.requestPageStore;
    context.enter(store); context.requests[0].resolve(page([4, 3], cursor(3))); await settle();
    store.loadMore(); context.requests[1].resolve(page([2, 1], cursor(1))); await settle();
    store.refresh();
    context.requests[2].resolve(page([5, 4], cursor(4))); await settle();
    assert.deepEqual(ids(store), [4, 3, 2, 1]);
    assert.equal(store.getSnapshot().nextCursor, cursor(1));
    assert.equal(context.requests[3].cursor, cursor(4));
    context.requests[3].resolve(page([2, 1], cursor(1))); await settle();
    assert.deepEqual(ids(store), [5, 4, 2, 1]);
    assert.equal(store.getSnapshot().nextCursor, cursor(1));
});

test('상세에 다녀와도 기존 목록을 유지하고 갱신 중 연속 이벤트는 한 번으로 합친다', async () => {
    const context = driver(), store = context.requestPageStore;
    context.enter(store); context.requests[0].resolve(page([3, 2], cursor(2))); await settle();
    context.enter(store); store.refresh(); store.refresh();
    assert.deepEqual(ids(store), [3, 2]);
    assert.equal(context.requests.length, 2);
    context.requests[1].resolve(page([3, 2], cursor(2))); await settle();
    assert.equal(context.requests.length, 3);
    context.requests[2].resolve(page([3, 2], cursor(2))); await settle();
    assert.deepEqual(ids(store), [3, 2]);
    assert.equal(store.getSnapshot().isRefreshing, false);
});

test('갱신 전에 시작한 다음 페이지의 늦은 성공과 실패를 무시한다', async () => {
    for (const fail of [false, true]) {
        const context = driver(), store = context.requestPageStore;
        context.enter(store); context.requests[0].resolve(page([3, 2], cursor(2))); await settle();
        store.loadMore(); store.refresh();
        context.requests[2].resolve(page([4, 3], cursor(3))); await settle();
        context.requests[3].resolve(page([2, 1], cursor(1))); await settle();
        if (fail) context.requests[1].reject(new Error()); else context.requests[1].resolve(page([1]));
        await settle();
        assert.deepEqual(ids(store), [4, 3, 2, 1]);
        assert.equal(store.getSnapshot().nextCursor, cursor(1));
        assert.equal(store.getSnapshot().isLoadingMore, false);
        assert.equal(context.messages.length, 0);
    }
});

test('추가 로딩과 범위 갱신 실패 시 기존 데이터와 커서를 유지하고 다시 시도한다', async () => {
    const context = driver(), store = context.requestPageStore;
    context.enter(store); context.requests[0].resolve(page([3, 2], cursor(2))); await settle();
    store.loadMore(); context.requests[1].reject(new Error()); await settle();
    assert.deepEqual(ids(store), [3, 2]);
    assert.equal(store.getSnapshot().hasLoadMoreError, true);
    store.loadMore(); assert.equal(context.requests[2].cursor, cursor(2));
    const tail = cursor(10, '1793491200123455');
    context.requests[2].resolve(page([1], tail)); await settle();
    store.refresh(); context.requests[3].resolve(page([4, 3], cursor(3))); await settle();
    context.requests[4].reject(new Error()); await settle();
    assert.deepEqual(ids(store), [3, 2, 1]);
    assert.equal(store.getSnapshot().nextCursor, tail);
    assert.equal(store.getSnapshot().isRefreshing, false);
});

test('로그인 만료는 로그인으로 보내고 계정 변경 시 이전 목록과 늦은 응답을 버린다', async () => {
    const context = driver(), store = context.requestPageStore;
    context.enter(store); context.requests[0].resolve(page([3, 2], cursor(2))); await settle();
    store.loadMore(); context.requests[1].reject('AUTH'); await settle();
    assert.equal(context.logins(), 1); assert.equal(context.messages.length, 0);
    store.refresh();
    context.changeAccount('driver-2'); context.enter(store);
    assert.equal(store.getSnapshot().state.status, 'loading');
    context.requests[3].resolve(page([10])); await settle();
    context.requests[2].resolve(page([4, 3], cursor(3))); await settle();
    assert.deepEqual(ids(store), [10]);
    assert.equal(store.getSnapshot().nextCursor, null);
});

test('배차 요청과 진행 중 및 완료 목록은 각각 독립된 커서를 사용한다', async () => {
    const context = driver();
    const stores = [context.requestPageStore, context.inProgressPageStore, context.deliveredPageStore];
    stores.forEach(context.enter);
    assert.deepEqual(context.requests.map(request => request.group), ['REQUESTED', 'IN_PROGRESS', 'DELIVERED']);
    context.requests.forEach((request, index) => request.resolve(page([index + 1], cursor(index + 1)))); await settle();
    context.inProgressPageStore.loadMore();
    assert.equal(context.requests[3].group, 'IN_PROGRESS');
    assert.equal(context.requests[3].cursor, cursor(2));
    context.requests[3].resolve(page([10])); await settle();
    assert.deepEqual(ids(context.inProgressPageStore), [2, 10]);
    assert.equal(context.deliveredPageStore.getSnapshot().nextCursor, cursor(3));
});

test('웹 목록은 size=20과 상태 그룹을 보내고 내 정보의 전체 조회는 페이지 옵션을 보내지 않는다', async () => {
    const requests = [];
    const { deliveryRepository } = load('api/deliveryRepository.ts', {
        './http': { apiCall: async (url, options) => { requests.push({ url, options }); return page([1]); }, deliveryTokenStore: {} }
    });
    await deliveryRepository.getRequestPage('next');
    await deliveryRepository.getShipmentPage('DELIVERED', 'done');
    await deliveryRepository.getShipments();
    assert.deepEqual({ ...requests[0].options.query }, { size: 20, cursor: 'next' });
    assert.deepEqual({ ...requests[1].options.query }, { size: 20, cursor: 'done', group: 'DELIVERED' });
    assert.equal(requests[2].options.query, undefined);
    assert.ok(requests.every(request => request.options.auth === 'delivery'));
});

test('새 요청으로 페이지가 밀려도 이전에 읽은 마지막 요청까지 갱신한다', async () => {
    const context = driver(), store = context.requestPageStore;
    context.enter(store); context.requests[0].resolve(page([4, 3], cursor(3))); await settle();
    store.loadMore(); context.requests[1].resolve(page([2, 1], cursor(1))); await settle();
    store.refresh(); context.requests[2].resolve(page([5, 4], cursor(4))); await settle();
    context.requests[3].resolve(page([3, 2], cursor(2))); await settle();
    assert.equal(context.requests.length, 5);
    context.requests[4].resolve(page([1, 9], cursor(9, '1793491200123455'))); await settle();
    assert.deepEqual(ids(store), [5, 4, 3, 2, 1, 9]);
    assert.equal(store.getSnapshot().nextCursor, cursor(9, '1793491200123455'));
});

test('이전 마지막 요청이 사라져도 커서 경계를 지나면 전체 이력을 읽지 않고 멈춘다', async () => {
    const context = driver(), store = context.requestPageStore;
    context.enter(store); context.requests[0].resolve(page([8, 7], cursor(7))); await settle();
    store.loadMore(); context.requests[1].resolve(page([6, 5], cursor(5))); await settle();
    store.refresh(); context.requests[2].resolve(page([9, 8], cursor(8))); await settle();
    context.requests[3].resolve(page([7, 6], cursor(6))); await settle();
    assert.equal(context.requests.length, 5);
    context.requests[4].resolve(page([4, 3], cursor(3))); await settle();
    assert.deepEqual(ids(store), [9, 8, 7, 6, 4, 3]);
    assert.equal(context.requests.length, 5);
    assert.equal(store.getSnapshot().isRefreshing, false);
});

test('읽었던 요청이 모두 사라지면 남은 이력의 개수를 채우려고 추가 조회하지 않는다', async () => {
    const context = driver(), store = context.requestPageStore;
    context.enter(store); context.requests[0].resolve(page([8, 7], cursor(7))); await settle();
    store.loadMore(); context.requests[1].resolve(page([6, 5], cursor(5))); await settle();
    store.refresh(); context.requests[2].resolve(page([4, 3], cursor(3))); await settle();
    assert.equal(context.requests.length, 3);
    assert.deepEqual(ids(store), [4, 3]);
    assert.equal(store.getSnapshot().isRefreshing, false);
});

test('커서 경계는 ID보다 시각을 먼저 비교하고 같은 시각에는 큰 ID부터 읽는다', async () => {
    const context = driver(), store = context.requestPageStore;
    context.enter(store); context.requests[0].resolve(page([8, 7], cursor(7))); await settle();
    store.refresh(); context.requests[1].resolve(page([10, 1], cursor(1, '1793491200123457'))); await settle();
    assert.equal(context.requests.length, 3);
    context.requests[2].resolve(page([9, 8], cursor(8))); await settle();
    assert.equal(context.requests.length, 4);
    context.requests[3].resolve(page([7, 6], cursor(6))); await settle();
    assert.deepEqual(ids(store), [10, 1, 9, 8, 7, 6]);
    assert.equal(store.getSnapshot().isRefreshing, false);
});

test('마지막 페이지까지 읽었던 목록은 새 요청이 늘어도 끝까지 갱신한다', async () => {
    const context = driver(), store = context.requestPageStore;
    context.enter(store); context.requests[0].resolve(page([4, 3], cursor(3))); await settle();
    store.loadMore(); context.requests[1].resolve(page([2, 1])); await settle();
    store.refresh(); context.requests[2].resolve(page([5, 4], cursor(4))); await settle();
    context.requests[3].resolve(page([3, 2], cursor(2))); await settle();
    assert.equal(context.requests.length, 5);
    context.requests[4].resolve(page([1])); await settle();
    assert.deepEqual(ids(store), [5, 4, 3, 2, 1]);
    assert.equal(store.getSnapshot().nextCursor, null);
});

test('빈 목록 뒤에 처음 요청이 생기면 첫 페이지만 읽는다', async () => {
    const context = driver(), store = context.requestPageStore;
    context.enter(store); context.requests[0].resolve(page([])); await settle();
    store.refresh(); context.requests[1].resolve(page([4, 3], cursor(3))); await settle();
    assert.deepEqual(ids(store), [4, 3]);
    assert.equal(context.requests.length, 2);
    assert.equal(store.getSnapshot().isRefreshing, false);
});
