#!/usr/bin/env node
// Node 22+, no dependencies. Tokens and fixture SQL belong outside Git.
import http from 'node:http';
import https from 'node:https';
import { promises as fs } from 'node:fs';
import path from 'node:path';
import { randomUUID } from 'node:crypto';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { pathToFileURL } from 'node:url';

const exec = promisify(execFile);
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
const stamp = () => new Date().toISOString();

function parseArgs(argv) {
  const [command, ...rest] = argv;
  const options = {};
  for (let i = 0; i < rest.length; i += 2) {
    if (!rest[i].startsWith('--') || rest[i + 1] === undefined) throw new Error('Use --option value pairs.');
    options[rest[i].slice(2)] = rest[i + 1];
  }
  return { command, options };
}

function number(options, key, fallback, min = 0) {
  const value = Number(options[key] ?? fallback);
  if (!Number.isFinite(value) || value < min) throw new Error(`Invalid --${key}`);
  return value;
}

function transport(url) { return url.protocol === 'https:' ? https : http; }

export function requestJson(base, endpoint, token, timeoutMs = 10000) {
  const url = new URL(base.replace(/\/$/, '') + endpoint);
  const started = performance.now();
  return new Promise((resolve, reject) => {
    const req = transport(url).get(url, {
      agent: false, headers: { Authorization: `Bearer ${token}`, Accept: 'application/json' },
    }, (res) => {
      let body = '';
      res.setEncoding('utf8');
      res.on('data', (chunk) => { body += chunk; if (body.length > 10000000) req.destroy(new Error('Body too large')); });
      res.on('end', () => {
        try { resolve({ status: res.statusCode, body: JSON.parse(body), ms: performance.now() - started }); }
        catch { reject(new Error(`Non-JSON response (${res.statusCode})`)); }
      });
      res.on('error', reject);
    });
    req.setTimeout(timeoutMs, () => req.destroy(new Error('JSON request timeout')));
    req.on('error', reject);
  });
}

export function openSse(base, token, state, timeoutMs = 10000) {
  const url = new URL(base.replace(/\/$/, '') + '/api/delivery/requests/events');
  const started = performance.now();
  return new Promise((resolve, reject) => {
    let settled = false;
    const connection = { live: false, intentional: false, heartbeat: 0, changes: 0 };
    const settle = (value) => { if (!settled) { settled = true; clearTimeout(timer); resolve(value); } };
    const fail = (error) => {
      if (!settled) { settled = true; clearTimeout(timer); connection.close?.(); reject(error); }
    };
    const req = transport(url).get(url, {
      agent: false, headers: { Authorization: `Bearer ${token}`, Accept: 'text/event-stream' },
    }, (res) => {
      connection.status = res.statusCode;
      connection.contentType = res.headers['content-type'] ?? '';
      let buffer = '';
      res.setEncoding('utf8');
      res.on('data', (chunk) => {
        buffer += chunk;
        if (res.statusCode !== 200) return;
        buffer = buffer.replace(/\r\n/g, '\n');
        let boundary;
        while ((boundary = buffer.indexOf('\n\n')) !== -1) {
          const event = buffer.slice(0, boundary);
          buffer = buffer.slice(boundary + 2);
          if (event.startsWith(':')) connection.heartbeat++;
          const name = /(?:^|\n)event:\s*([^\n]+)/.exec(event)?.[1];
          if (name === 'connected' && !settled) {
            if (!connection.contentType.includes('text/event-stream')) return fail(new Error('Wrong SSE Content-Type'));
            connection.live = true;
            connection.connectedMs = performance.now() - started;
            state.connections.push(connection);
            settle(connection);
          }
          if (name === 'delivery-requests-changed') connection.changes++;
        }
      });
      res.on('end', () => {
        if (res.statusCode !== 200) {
          try { connection.body = JSON.parse(buffer); settle(connection); }
          catch { fail(new Error(`Non-JSON SSE rejection (${res.statusCode})`)); }
        } else if (!settled) fail(new Error('Stream ended before connected event'));
      });
      res.on('close', () => {
        if (connection.live && !connection.intentional) state.unexpectedClosures.push({ at: stamp(), phase: state.phase });
        connection.live = false;
      });
      res.on('error', (error) => { if (!connection.intentional) fail(error); });
    });
    connection.close = () => { connection.intentional = true; connection.live = false; req.destroy(); };
    const timer = setTimeout(() => req.destroy(new Error('SSE connection/connected-event timeout')), timeoutMs);
    req.on('error', fail);
  });
}

function live(state) { return state.connections.filter((item) => item.live).length; }
function requireCondition(value, message) { if (!value) throw new Error(message); }
function percentiles(values) {
  const sorted = [...values].sort((a, b) => a - b);
  const p = (q) => sorted.length ? Number(sorted[Math.max(0, Math.ceil(sorted.length * q) - 1)].toFixed(3)) : null;
  return { count: sorted.length, p50Ms: p(.5), p95Ms: p(.95), p99Ms: p(.99), maxMs: p(1) };
}

async function resources(pid, jcmd) {
  if (!pid) return null;
  try {
    const [{ stdout: processInfo }, { stdout: heap }] = await Promise.all([
      exec('ps', ['-p', String(pid), '-o', 'rss=,pcpu=']),
      exec(jcmd, [String(pid), 'GC.heap_info'], { timeout: 5000 }),
    ]);
    const [rssKiB, cpuPercent] = processInfo.trim().split(/\s+/).map(Number);
    return { rssBytes: rssKiB * 1024, cpuPercent,
      heapUsedBytes: Number(/garbage-first heap\s+total \d+K, used (\d+)K/.exec(heap)?.[1]) * 1024 || null };
  } catch (error) { return { error: error.message.split('\n')[0] }; }
}

export async function run(config) {
  const state = { phase: 'baseline', connections: [], unexpectedClosures: [] };
  const samples = [], api = [], checks = [];
  const started = stamp();
  let failure = null;
  let recovery = null;
  const sample = async () => {
    const data = { at: stamp(), phase: state.phase, clientLiveConnections: live(state),
      resources: await resources(config.pid, config.jcmd ?? 'jcmd') };
    samples.push(data);
    if (config.output) await fs.appendFile(path.join(config.output, 'samples.jsonl'), JSON.stringify(data) + '\n');
    return data;
  };
  const list = async (phase) => {
    const result = await requestJson(config.base, '/api/delivery/requests', config.tokens[0]);
    api.push({ phase, at: stamp(), status: result.status, ms: result.ms });
    requireCondition(result.status === 200 && Array.isArray(result.body), `List API failed (${result.status})`);
    return result;
  };
  const checkReject = async (token, status, code) => {
    const connection = await openSse(config.base, token, state);
    connection.close();
    requireCondition(connection.status === status && connection.body?.code === code
      && connection.contentType.includes('application/json'), `Expected ${status}/${code}, got ${connection.status}`);
    checks.push({ phase: state.phase, status, code, passed: true });
  };
  const retryConnect = async (token) => {
    const deadline = performance.now() + config.cleanupSeconds * 1000;
    let attempts = 0;
    while (performance.now() < deadline) {
      attempts++;
      const connection = await openSse(config.base, token, state);
      if (connection.status === 200) return { connection, attempts };
      connection.close();
      requireCondition(connection.status === 429 || connection.status === 503, `Unexpected retry status ${connection.status}`);
      await sleep(500);
    }
    throw new Error('No slot restored within cleanup timeout');
  };
  if (config.output) await fs.mkdir(config.output, { recursive: true });
  try {
    for (let i = 0; i < 20; i++) await list('baseline');
    await sample();
    state.phase = 'driver-limit';
    const first = await openSse(config.base, config.tokens[0], state);
    const second = await openSse(config.base, config.tokens[0], state);
    requireCondition(first.status === 200 && second.status === 200, 'First two driver connections must succeed');
    await checkReject(config.tokens[0], 429, 'DRIVER_LIMIT_EXCEEDED');
    first.close(); second.close();
    // Wait for both old subscriptions to disappear before using this driver in the main cohort.
    const restored = await retryConnect(config.tokens[0]);
    const restoredSecond = await retryConnect(config.tokens[0]);
    restored.connection.close(); restoredSecond.connection.close();
    // Heartbeat is the normal cleanup path; allow it to detect the preceding closed sockets.
    await sleep(config.preCleanupSeconds * 1000);
    state.phase = 'ramp';
    const rampStart = performance.now();
    const cohort = [];
    const inFlight = new Set();
    let rampFailure = null;
    for (let i = 0; i < config.connections; i++) {
      if (rampFailure) throw rampFailure;
      while (inFlight.size >= config.openConcurrency) await Promise.race(inFlight);
      const pending = openSse(config.base, config.tokens[i], state).then((connection) => {
        requireCondition(connection.status === 200, `Ramp connection ${i + 1} rejected (${connection.status})`);
        cohort.push(connection);
      }).catch((error) => { rampFailure ??= error; }).finally(() => inFlight.delete(pending));
      inFlight.add(pending);
      const nextAt = rampStart + (i + 1) * config.rampSeconds * 1000 / config.connections;
      if (nextAt > performance.now()) await sleep(nextAt - performance.now());
    }
    await Promise.all(inFlight);
    if (rampFailure) throw rampFailure;
    requireCondition(live(state) === config.connections, `Expected ${config.connections} live streams, got ${live(state)}`);
    state.phase = 'at-capacity';
    await checkReject(config.tokens[config.connections], 503, 'TOTAL_LIMIT_EXCEEDED');
    await sample();
    const holdStart = performance.now();
    let nextSample = holdStart;
    while (performance.now() - holdStart < config.holdSeconds * 1000) {
      requireCondition(live(state) === config.connections, 'A stream closed during capacity hold');
      await list('at-capacity');
      if (performance.now() >= nextSample) { await sample(); nextSample = performance.now() + 5000; }
      await sleep(config.listIntervalMs);
    }
    requireCondition(live(state) === config.connections, 'Capacity hold ended below target');
    state.phase = 'recovery';
    const before = performance.now();
    cohort[0].close();
    const result = await retryConnect(config.tokens[config.connections]);
    recovery = { attempts: result.attempts, elapsedMs: performance.now() - before, passed: true };
    await checkReject(config.tokens[config.connections + 1], 503, 'TOTAL_LIMIT_EXCEEDED');
    await list('recovery');
    await sample();
    requireCondition(state.unexpectedClosures.length === 0, 'Unexpected stream closures occurred');
  } catch (error) { failure = error.message; }
  finally {
    state.phase = 'cleanup';
    state.connections.forEach((connection) => connection.close());
    await sleep(config.postCleanupSeconds * 1000);
    await sample();
  }
  const summary = {
    startedAt: started, finishedAt: stamp(), passed: failure === null, failure,
    baseUrl: config.base, requestedConnections: config.connections, holdSeconds: config.holdSeconds,
    configuredTotalLimitExpected: config.connections, fixtureDriverCount: config.tokens.length,
    checks, recovery, unexpectedClosures: state.unexpectedClosures,
    api: Object.fromEntries(['baseline', 'at-capacity', 'recovery'].map((phase) =>
      [phase, percentiles(api.filter((item) => item.phase === phase).map((item) => item.ms))])),
    samples, heartbeatCommentsReceived: state.connections.reduce((n, item) => n + item.heartbeat, 0),
    scope: 'connection-capacity; X=0; heartbeat retained; small periodic list probes; no phone-screen or event-throughput measurement',
    limitations: ['Heap sampled without forced GC; post-close heap is not a leak verdict.',
      'Client/server share hardware for a local run.', 'Public-server subscriber count is not directly exposed.'],
  };
  if (config.output) {
    await fs.writeFile(path.join(config.output, 'summary.json'), JSON.stringify(summary, null, 2) + '\n');
    await fs.writeFile(path.join(config.output, 'api.jsonl'), api.map((item) => JSON.stringify(item)).join('\n') + '\n');
  }
  return summary;
}

async function prepare(options) {
  const dir = options.output;
  if (!dir) throw new Error('--output is required');
  const count = number(options, 'drivers', 1002, 4);
  requireCondition(Number.isInteger(count), '--drivers must be an integer');
  await fs.mkdir(dir, { recursive: true });
  const prefix = 'ssel' + randomUUID().replaceAll('-', '').slice(0, 10);
  const tokens = Array.from({ length: count }, () => randomUUID());
  const names = tokens.map((_, i) => prefix + String(i).padStart(5, '0'));
  const rows = tokens.map((token, i) => `('${names[i]}','sse-load-login-disabled','010-0000-0000','00가0000','다마스','000-00-00000','${token}')`);
  const seed = 'START TRANSACTION;\nINSERT INTO delivery_member (login_id,password,phone_number,license_plate_number,car_type,business_registration_number,token) VALUES\n'
    + rows.join(',\n') + ';\nCOMMIT;\n';
  const cleanup = 'START TRANSACTION;\nDELETE FROM delivery_member WHERE login_id IN ('
    + names.map((name) => `'${name}'`).join(',') + ') AND token IN ('
    + tokens.map((token) => `'${token}'`).join(',') + ');\nCOMMIT;\n';
  for (const [name, content] of Object.entries({ 'tokens.private.json': JSON.stringify({ prefix, tokens }),
    'seed.private.sql': seed, 'cleanup.private.sql': cleanup })) {
    await fs.writeFile(path.join(dir, name), content, { mode: 0o600, flag: 'wx' });
  }
  console.log(JSON.stringify({ fixturePrefix: prefix, drivers: count, output: dir }));
}

async function main() {
  const { command, options } = parseArgs(process.argv.slice(2));
  if (command === 'prepare') return prepare(options);
  if (command !== 'run') throw new Error('Use prepare or run.');
  requireCondition(options.base && options['token-file'] && options.output, '--base, --token-file and --output are required');
  const tokens = JSON.parse(await fs.readFile(options['token-file'], 'utf8')).tokens;
  const connections = number(options, 'connections', 1000, 3);
  requireCondition(Number.isInteger(connections) && tokens.length >= connections + 2, 'Need N+2 distinct driver tokens');
  const config = { base: options.base.replace(/\/$/, ''), tokens, connections, output: options.output,
    rampSeconds: number(options, 'ramp-seconds', 20), holdSeconds: number(options, 'hold-seconds', 45),
    cleanupSeconds: number(options, 'cleanup-seconds', 40, 1),
    // A closed peer can be detected only on the second heartbeat write (15s each).
    preCleanupSeconds: number(options, 'pre-cleanup-seconds', 31),
    postCleanupSeconds: number(options, 'post-cleanup-seconds', 31),
    listIntervalMs: number(options, 'list-interval-ms', 500, 10),
    openConcurrency: number(options, 'open-concurrency', 25, 1), pid: options.pid, jcmd: options.jcmd,
  };
  requireCondition(config.rampSeconds + config.holdSeconds + config.cleanupSeconds < 115,
    'Ramp+hold+recovery budget must stay below the server 120s connection timeout.');
  const summary = await run(config);
  console.log(JSON.stringify({ passed: summary.passed, failure: summary.failure,
    requestedConnections: connections, api: summary.api, recovery: summary.recovery, output: options.output }, null, 2));
  if (!summary.passed) process.exitCode = 1;
}

if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) {
  main().catch((error) => { console.error(error.message); process.exitCode = 1; });
}
