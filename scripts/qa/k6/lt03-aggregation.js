// =============================================================================
// LT-03 — 분석 집계 워커 처리량
//
// 시나리오 (backend/docs/qa/03-load-and-failure-scenarios.md §2.3):
//   1) hourly 집계 트리거 (force=true) — 30K notification_logs row 처리
//   2) hourly 멱등 재호출 — 같은 bucket 으로 다시 호출, 결과 row 수 변화 없음
//   3) daily 롤업 트리거 (force=true) — 4종 daily 테이블 채워짐
//
// 임계치:
//   - hourly 단일 호출 < 5초 (LT-03 운영 임계)
//   - daily 4종 롤업 총 시간 < 30초 (운영 임계)
//   - 모든 호출 200 응답
//
// 실행:
//   k6 run -e BUCKET_HOUR=2026-05-14T00:00:00 scripts/qa/k6/lt03-aggregation.js
//
// 시드: 시드 스크립트로 30K notification_logs row 미리 적재 필요
//   scripts/qa/seed/seed-load-test-fixtures.sql
//
// 포트폴리오 카드:
//   docs/optimizations/2026-05-11_aggregation-worker-throughput.md (BEFORE-ONLY)
//
// 주의:
//   본 시나리오는 부하 분산형이 아닌 "한 번 호출 후 시간 측정" 형이므로
//   constant-arrival-rate 가 아닌 단일 iteration 으로 실행.
// =============================================================================

import http from 'k6/http';
import { check } from 'k6';
import { Trend } from 'k6/metrics';
import { BASE_URL } from './common.js';

const BUCKET_HOUR = __ENV.BUCKET_HOUR || '2026-05-14T00:00:00';

const hourlyTrend = new Trend('hourly_aggregate_duration_ms', true);
const hourlyIdempotentTrend = new Trend('hourly_idempotent_duration_ms', true);
const dailyTrend = new Trend('daily_rollup_duration_ms', true);

export const options = {
  scenarios: {
    single_run: {
      executor: 'per-vu-iterations',
      vus: 1,
      iterations: 1,
      maxDuration: '5m',
    },
  },
  thresholds: {
    'hourly_aggregate_duration_ms': ['p(95)<5000', 'avg<4000'],
    'hourly_idempotent_duration_ms': ['p(95)<5000'],
    'daily_rollup_duration_ms': ['p(95)<30000', 'avg<10000'],
    checks: ['rate==1.0'],
  },
  tags: {
    scenario: 'LT-03',
    target: 'aggregation-worker',
  },
};

export default function () {
  // 1) hourly force 재집계
  const hourlyUrl =
    `${BASE_URL}/internal/stats/aggregate/hourly` +
    `?bucketHour=${encodeURIComponent(BUCKET_HOUR)}&force=true`;
  const hourlyStart = Date.now();
  const hourlyRes = http.post(hourlyUrl, null, {
    headers: { 'Content-Type': 'application/json' },
  });
  hourlyTrend.add(Date.now() - hourlyStart);
  check(hourlyRes, {
    'hourly: status 200': (r) => r.status === 200,
  });

  // 2) hourly 멱등 재호출 — 같은 bucket, force=false 도 같은 결과
  const idemUrl =
    `${BASE_URL}/internal/stats/aggregate/hourly` +
    `?bucketHour=${encodeURIComponent(BUCKET_HOUR)}&force=false`;
  const idemStart = Date.now();
  const idemRes = http.post(idemUrl, null, {
    headers: { 'Content-Type': 'application/json' },
  });
  hourlyIdempotentTrend.add(Date.now() - idemStart);
  check(idemRes, {
    'hourly idempotent: status 200': (r) => r.status === 200,
  });

  // 3) daily 롤업 force 재집계
  const dailyDate = BUCKET_HOUR.substring(0, 10); // YYYY-MM-DD
  const dailyUrl =
    `${BASE_URL}/internal/stats/rollup/daily` +
    `?bucketDate=${encodeURIComponent(dailyDate)}&force=true`;
  const dailyStart = Date.now();
  const dailyRes = http.post(dailyUrl, null, {
    headers: { 'Content-Type': 'application/json' },
  });
  dailyTrend.add(Date.now() - dailyStart);
  check(dailyRes, {
    'daily: status 200': (r) => r.status === 200,
  });
}

export function handleSummary(data) {
  const m = data.metrics;
  const h = m.hourly_aggregate_duration_ms?.values;
  const hI = m.hourly_idempotent_duration_ms?.values;
  const d = m.daily_rollup_duration_ms?.values;

  const fmt = (v) => (v ? v.toFixed(0) : 'n/a');

  const summary = `
============================================================
LT-03 — Aggregation Worker Throughput
============================================================
bucketHour : ${BUCKET_HOUR}
시드 가정  : 30K notification_logs row × 캠페인 5 × 채널 2

[1] Hourly force 재집계
    avg   : ${fmt(h?.avg)} ms       (limit: < 4000 ms)
    p95   : ${fmt(h?.['p(95)'])} ms (limit: < 5000 ms)

[2] Hourly 멱등 재호출 (force=false, 같은 bucket)
    avg   : ${fmt(hI?.avg)} ms
    p95   : ${fmt(hI?.['p(95)'])} ms (limit: < 5000 ms)
    → 첫 호출과 같은 결과 (row 변화 없음) — UPSERT 멱등성 검증

[3] Daily 4종 롤업 force 재집계
    avg   : ${fmt(d?.avg)} ms        (limit: < 10000 ms)
    p95   : ${fmt(d?.['p(95)'])} ms  (limit: < 30000 ms)

→ docs/optimizations/2026-05-11_aggregation-worker-throughput.md
  의 Result 섹션에 인용 + 첨부:
  k6 run --summary-export=lt03-summary.json scripts/qa/k6/lt03-aggregation.js
============================================================
`;
  return {
    stdout: summary,
    'lt03-summary.json': JSON.stringify(data),
  };
}
