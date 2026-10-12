const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { join } = require('node:path');
const { test } = require('node:test');
const { runInNewContext } = require('node:vm');
const ts = require('typescript');

const filename = join(__dirname, '../src/features/requests/useRequests.ts');
const compiled = ts.transpileModule(readFileSync(filename, 'utf8'), {
  compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS },
}).outputText;

function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((done, fail) => { resolve = done; reject = fail; });
  return { promise, resolve, reject };
}

function summary(deliveryId) {
  return {
    deliveryId,
    itemName: `가구 ${deliveryId}`,
    category: 'CHAIR',
    pickupAddress: '출발지',
    deliveryAddress: '도착지',
    deliveryFee: 10000,
    status: 'REQUESTED',
  };
}

const flush = () => new Promise((resolve) => setImmediate(resolve));

// React 훅 저장소와 목록 API만 모의한다. 실제 훅의 제어 흐름을 실행하며 네트워크는 사용하지 않는다.
function mountRequests() {
  const slots = [];
  const effects = [];
  const requests = [];
  const rejected = new Set();
  let cursor = 0;
  let active = 0;
  let peakActive = 0;
  let unmounted = false;

  const react = {
    useState(initial) {
      const index = cursor++;
      if (!slots[index]) {
        const slot = { value: typeof initial === 'function' ? initial() : initial };
        slot.set = (next) => {
          assert.equal(unmounted, false, 'unmounted hook must not update state');
          slot.value = typeof next === 'function' ? next(slot.value) : next;
        };
        slots[index] = slot;
      }
      return [slots[index].value, slots[index].set];
    },
    useRef(initial) {
      const index = cursor++;
      slots[index] ??= { current: initial };
      return slots[index];
    },
    useCallback(callback, dependencies) {
      const index = cursor++;
      const previous = slots[index];
      if (!previous || dependencies.some((value, i) => value !== previous.dependencies[i])) {
        slots[index] = { callback, dependencies };
      }
      return slots[index].callback;
    },
    useEffect(effect, dependencies) {
      const index = cursor++;
      const previous = slots[index];
      if (!previous || dependencies.some((value, i) => value !== previous.dependencies[i])) {
        const slot = { dependencies, cleanup: previous?.cleanup };
        slots[index] = slot;
        effects.push(() => {
          slot.cleanup?.();
          slot.cleanup = effect();
        });
      }
    },
  };

  const dependencies = {
    react,
    '@/api/deliveryApi': {
      deliveryApi: {
        getRequests() {
          active += 1;
          peakActive = Math.max(peakActive, active);
          const request = deferred();
          requests.push(request);
          return request.promise.finally(() => { active -= 1; });
        },
      },
    },
    '@/lib/errorMessage': { errorMessage: (error) => error.message },
    './rejectedStore': {
      rejectedStore: {
        add: (id) => rejected.add(id),
        filter: (items) => items.filter((item) => !rejected.has(item.deliveryId)),
      },
    },
  };
  const module = { exports: {} };
  runInNewContext(compiled, {
    module,
    exports: module.exports,
    require(name) {
      assert.ok(name in dependencies, `unexpected dependency: ${name}`);
      return dependencies[name];
    },
  }, { filename });

  function render() {
    cursor = 0;
    const result = module.exports.useRequests();
    for (const effect of effects.splice(0)) effect();
    return result;
  }

  render();
  return {
    render,
    requests,
    get active() { return active; },
    get peakActive() { return peakActive; },
    unmount() {
      unmounted = true;
      for (const slot of slots) slot?.cleanup?.();
    },
  };
}

test('initial load, SSE reloads and manual refresh share one queued request', async () => {
  const hook = mountRequests();
  hook.render().reload();
  hook.render().reload();
  let refreshFinished = false;
  const refresh = hook.render().refresh().then(() => { refreshFinished = true; });

  assert.equal(hook.requests.length, 1);
  assert.equal(hook.render().refreshing, true);
  hook.requests[0].resolve([summary(1)]);
  await flush();

  assert.equal(hook.requests.length, 2);
  assert.equal(refreshFinished, false);
  assert.equal(hook.render().items.length, 0, 'superseded initial response must not be applied');
  hook.requests[1].resolve([summary(2)]);
  await refresh;

  assert.deepEqual(hook.render().items.map((item) => item.deliveryId), [2]);
  assert.equal(hook.render().loading, false);
  assert.equal(hook.render().refreshing, false);
  assert.equal(hook.requests.length, 2);
  assert.equal(hook.active, 0);
  assert.equal(hook.peakActive, 1);
});

test('changes during a queued request are retained for the next sequential request', async () => {
  const hook = mountRequests();
  hook.requests[0].resolve([summary(1)]);
  await flush();

  let firstRefreshFinished = false;
  const first = hook.render().refresh().then(() => { firstRefreshFinished = true; });
  hook.render().reload();
  hook.render().reload();
  hook.requests[1].resolve([summary(1), summary(2)]);
  await flush();

  assert.equal(hook.requests.length, 3);
  assert.equal(firstRefreshFinished, true, 'completed callers must not wait for the queue to become idle');
  hook.render().reload();
  hook.render().reload();
  hook.requests[2].resolve([summary(1), summary(2), summary(3)]);
  await flush();

  assert.equal(hook.requests.length, 4);
  assert.deepEqual(hook.render().items.map((item) => item.deliveryId), [1]);
  hook.requests[3].resolve([summary(1), summary(2), summary(3), summary(4)]);
  await flush();
  await first;

  assert.deepEqual(hook.render().items.map((item) => item.deliveryId), [1, 2, 3, 4]);
  assert.deepEqual(Array.from(hook.render().newRequestIds), [2, 3, 4]);
  assert.equal(hook.requests.length, 4);
  assert.equal(hook.peakActive, 1);
});

test('a failed initial request releases the loader and allows a manual retry', async () => {
  const hook = mountRequests();
  hook.requests[0].reject(new Error('initial failure'));
  await flush();

  assert.equal(hook.render().loading, false);
  assert.equal(hook.render().error, 'initial failure');
  const retry = hook.render().refresh();
  assert.equal(hook.render().error, null);
  assert.equal(hook.requests.length, 2);
  hook.requests[1].resolve([summary(1)]);
  await retry;

  assert.deepEqual(hook.render().items.map((item) => item.deliveryId), [1]);
  assert.equal(hook.render().refreshing, false);
  assert.equal(hook.peakActive, 1);
});

test('queued work runs after failure and only the latest error is shown', async () => {
  const hook = mountRequests();
  hook.requests[0].resolve([summary(1)]);
  await flush();

  hook.render().reload();
  hook.render().reload();
  const refresh = hook.render().refresh();
  hook.requests[1].reject(new Error('superseded failure'));
  await flush();

  assert.equal(hook.requests.length, 3);
  assert.equal(hook.render().error, null);
  hook.requests[2].reject(new Error('latest failure'));
  await refresh;
  assert.equal(hook.render().error, 'latest failure');
  assert.equal(hook.render().refreshing, false);

  const retry = hook.render().refresh();
  hook.requests[3].resolve([summary(2)]);
  await retry;
  assert.equal(hook.render().error, null);
  assert.deepEqual(hook.render().items.map((item) => item.deliveryId), [2]);
  assert.equal(hook.peakActive, 1);
});

test('manual refreshes do not discard highlighting requested by a coalesced SSE reload', async () => {
  const hook = mountRequests();
  hook.requests[0].resolve([summary(1)]);
  await flush();

  hook.render().reload();
  const refresh = hook.render().refresh();
  hook.requests[1].resolve([summary(1), summary(2)]);
  await flush();
  hook.requests[2].resolve([summary(1), summary(2)]);
  await refresh;

  assert.deepEqual(Array.from(hook.render().newRequestIds), [2]);
  assert.equal(hook.peakActive, 1);
});

test('a local rejection made during a request remains hidden when the response arrives', async () => {
  const hook = mountRequests();
  hook.requests[0].resolve([summary(1), summary(2)]);
  await flush();

  hook.render().reload();
  hook.render().rejectLocal(2);
  hook.requests[1].resolve([summary(1), summary(2)]);
  await flush();

  assert.deepEqual(hook.render().items.map((item) => item.deliveryId), [1]);
  assert.equal(hook.peakActive, 1);
});

test('unmount settles queued callers without starting another request or updating state', async () => {
  const hook = mountRequests();
  const queued = hook.render().refresh();
  hook.unmount();
  await queued;

  hook.requests[0].resolve([summary(1)]);
  await flush();
  assert.equal(hook.requests.length, 1);
  assert.equal(hook.active, 0);
});
