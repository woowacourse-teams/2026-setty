import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { run, openSse } from './sse-connection-load.mjs';

async function withServer(callback, brokenRejection = false) {
  const clients = new Map();
  const server = http.createServer((req, res) => {
    const driver = req.headers.authorization;
    if (req.url.endsWith('/requests')) {
      res.writeHead(200, { 'Content-Type': 'application/json' }).end('[]');
      return;
    }
    const same = [...clients.values()].filter((value) => value === driver).length;
    if (clients.size === 3 || same === 2) {
      const status = clients.size === 3 ? 503 : 429;
      const code = clients.size === 3 ? 'TOTAL_LIMIT_EXCEEDED' : 'DRIVER_LIMIT_EXCEEDED';
      res.writeHead(brokenRejection ? 200 : status, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ code }));
      return;
    }
    clients.set(res, driver);
    res.writeHead(200, { 'Content-Type': 'text/event-stream' });
    // Exercise split SSE fields, CRLF boundaries and continuing comments.
    res.write('event:con');
    setImmediate(() => res.write('nected\r\ndata:{}\r\n\r\n'));
    const heartbeat = setInterval(() => res.write(':heartbeat\n\n'), 15);
    res.on('close', () => { clients.delete(res); clearInterval(heartbeat); });
  });
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const base = `http://127.0.0.1:${server.address().port}`;
  try { await callback(base); }
  finally {
    for (const res of clients.keys()) res.destroy();
    server.closeAllConnections();
    await new Promise((resolve) => server.close(resolve));
  }
}

function config(base) {
  return { base, tokens: ['one', 'two', 'three', 'four', 'five'], connections: 3,
    rampSeconds: .1, holdSeconds: .1, cleanupSeconds: 2,
    preCleanupSeconds: .03, postCleanupSeconds: .03, listIntervalMs: 10, openConcurrency: 2 };
}

test('runner validates both caps, maintains streams, measures APIs and recovers a slot', async () => {
  await withServer(async (base) => {
    const result = await run(config(base));
    assert.equal(result.passed, true, result.failure);
    assert.equal(result.checks.length, 3);
    assert.equal(result.recovery.passed, true);
    assert.equal(result.unexpectedClosures.length, 0);
    assert.equal(result.samples.find((sample) => sample.phase === 'at-capacity').clientLiveConnections, 3);
    assert.equal(result.samples.at(-1).clientLiveConnections, 0);
    assert.ok(result.api['at-capacity'].count > 0);
  });
});

test('runner fails instead of reporting success when an overload response is wrong', async () => {
  await withServer(async (base) => {
    const result = await run(config(base));
    assert.equal(result.passed, false);
    assert.match(result.failure, /before connected event/);
  }, true);
});

test('a closed port fails the connection attempt', async () => {
  const server = http.createServer();
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const port = server.address().port;
  await new Promise((resolve) => server.close(resolve));
  await assert.rejects(openSse(`http://127.0.0.1:${port}`, 'unused',
    { connections: [], unexpectedClosures: [], phase: 'test' }), /ECONNREFUSED/);
});
