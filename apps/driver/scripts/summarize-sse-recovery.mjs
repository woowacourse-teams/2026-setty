#!/usr/bin/env node

import { readFile } from 'node:fs/promises';

const inputPath = process.argv[2];

if (!inputPath) {
  console.error('사용법: node apps/driver/scripts/summarize-sse-recovery.mjs <측정 JSONL 경로>');
  process.exit(2);
}

const contents = await readFile(inputPath, 'utf8');
const records = contents
  .split(/\r?\n/)
  .map((line, index) => ({ line: line.trim(), lineNumber: index + 1 }))
  .filter(({ line }) => line.length > 0)
  .map(({ line, lineNumber }) => {
    try {
      return { ...JSON.parse(line), lineNumber };
    } catch (error) {
      throw new Error(`${lineNumber}행 JSON 파싱 실패: ${error.message}`);
    }
  });

if (records.length === 0) {
  console.error('측정 기록이 없습니다.');
  process.exit(1);
}

const samples = [];
let failedAttempts = 0;
let timeoutRuns = 0;
let incompleteRuns = 0;

for (const record of records) {
  failedAttempts += nonNegativeInteger(record.failedAttempts ?? 0, record, 'failedAttempts');
  if (record.timedOut === true) timeoutRuns += 1;

  if (record.screenMatchedAt === null || record.screenMatchedAt === undefined) {
    incompleteRuns += 1;
    continue;
  }

  const readyTime = timestamp(record.serverReadyAt, record, 'serverReadyAt');
  const matchedTime = timestamp(record.screenMatchedAt, record, 'screenMatchedAt');
  const recoveryMs = matchedTime - readyTime;
  if (recoveryMs < 0) {
    throw new Error(`${record.lineNumber}행 screenMatchedAt이 serverReadyAt보다 빠릅니다.`);
  }

  const serverIds = idSet(record.serverRequestIds, record, 'serverRequestIds');
  const rejectedIds = idSet(record.locallyRejectedIds ?? [], record, 'locallyRejectedIds');
  const screenIds = idSet(record.screenRequestIds, record, 'screenRequestIds');
  const expectedIds = new Set([...serverIds].filter((id) => !rejectedIds.has(id)));
  const staleIds = [...screenIds].filter((id) => !expectedIds.has(id));
  const missingIds = [...expectedIds].filter((id) => !screenIds.has(id));

  samples.push({
    run: record.run ?? samples.length + 1,
    recoveryMs,
    staleIds,
    missingIds,
  });
}

const durations = samples.map(({ recoveryMs }) => recoveryMs).sort((left, right) => left - right);
const p95 = durations.length === 0 ? null : durations[Math.ceil(durations.length * 0.95) - 1];
const maxStaleCount = samples.reduce((maximum, sample) => Math.max(maximum, sample.staleIds.length), 0);

console.log(`기록 행: ${records.length}`);
console.log(`완료 표본: ${samples.length}`);
console.log(`미완료 기록: ${incompleteRuns}`);
console.log(`시간 초과 회차: ${timeoutRuns}`);
console.log(`실패 시도: ${failedAttempts}`);
console.log(`복원 시간 P95: ${p95 === null ? '산출 불가' : `${p95}ms`}`);
console.log(`부적합 요청 잔존 최댓값: ${samples.length === 0 ? '산출 불가' : `${maxStaleCount}건`}`);
console.log('회차별:');

for (const sample of samples) {
  console.log(
    `  ${sample.run}: ${sample.recoveryMs}ms, ` +
      `부적합 잔존 ${sample.staleIds.length}건 [${sample.staleIds.join(',')}], ` +
      `누락 ${sample.missingIds.length}건 [${sample.missingIds.join(',')}]`,
  );
}

function timestamp(value, record, field) {
  const time = Date.parse(value);
  if (!Number.isFinite(time)) {
    throw new Error(`${record.lineNumber}행 ${field}에 ISO 8601 시각이 필요합니다.`);
  }
  return time;
}

function idSet(value, record, field) {
  if (!Array.isArray(value)) {
    throw new Error(`${record.lineNumber}행 ${field}에 ID 배열이 필요합니다.`);
  }
  return new Set(value.map(String));
}

function nonNegativeInteger(value, record, field) {
  if (!Number.isInteger(value) || value < 0) {
    throw new Error(`${record.lineNumber}행 ${field}는 0 이상의 정수여야 합니다.`);
  }
  return value;
}
