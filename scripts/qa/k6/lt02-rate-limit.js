// =============================================================================
// LT-02 — 레이트 리밋 동작 검증
//
// 시나리오 (backend/docs/qa/03-load-and-failure-scenarios.md §2.2):
//   POST /api/v1/events/actions @ 150 req/s, 1분 지속 (한도 100/s 초과)
//
// 기대:
//   - 한도 안 (100/s): 201 응답
//   - 한도 초과: 429 RATE_429 + Retry-After 헤더
//   - 한도 환산: 100 req/s × 60 = 6000 RPM (RedisRateLimiter sliding window)
//
// 실행:
//   k6 run -e API_KEY=pk_live_xxxx... -e LOG_ID=42 scripts/qa/k6/lt02-rate-limit.js
// =============================================================================

import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { BASE_URL, buildActionPayload, authHeaders } from './common.js';

const status201 = new Counter('status_201');
const status429 = new Counter('status_429');
const other = new Counter('status_other');
const retryAfterSet = new Counter('retry_after_set');

export const options = {
  scenarios: {
    burst_150rps: {
      executor: 'constant-arrival-rate',
      rate: 150,
      timeUnit: '1s',
      duration: '1m',
      preAllocatedVUs: 100,
      maxVUs: 300,
    },
  },
  thresholds: {
    // 한도 안: 5000~6500건 (분당 6000 RPM 한도 + sliding window 오차)
    // 한도 초과: 2500~4000건 (총 9000 건 중 한도 안 제외)
    status_201: ['count>3000'],   // 최소 3000건은 통과해야 한도가 너무 보수적이지 않음
    status_429: ['count>1000'],   // 최소 1000건은 차단되어야 한도가 동작
    status_other: ['count<10'],   // 401/500 등 비정상 응답 거의 없어야
    retry_after_set: ['count>0'], // 429 응답 시 Retry-After 헤더 1건 이상 세팅
  },
  tags: {
    scenario: 'LT-02',
    target: 'event-ingest-api',
  },
};

export default function () {
  const payload = buildActionPayload();
  const res = http.post(`${BASE_URL}/api/v1/events/actions`, payload, {
    headers: authHeaders(),
  });

  if (res.status === 201) {
    status201.add(1);
  } else if (res.status === 429) {
    status429.add(1);
    if (res.headers['Retry-After'] || res.headers['retry-after']) {
      retryAfterSet.add(1);
    }
  } else {
    other.add(1);
  }

  check(res, {
    'expected 201 or 429': (r) => r.status === 201 || r.status === 429,
    '429 has Retry-After': (r) =>
      r.status !== 429 || !!(r.headers['Retry-After'] || r.headers['retry-after']),
  });
}

export function handleSummary(data) {
  const m = data.metrics;
  const reqs = m.http_reqs?.values?.count;
  const c201 = m.status_201?.values?.count ?? 0;
  const c429 = m.status_429?.values?.count ?? 0;
  const cOther = m.status_other?.values?.count ?? 0;
  const cRetry = m.retry_after_set?.values?.count ?? 0;

  const summary = `
============================================================
LT-02 — Rate Limit @ 150 req/s for 1m (limit: 100 req/s)
============================================================
Total requests   : ${reqs}
  201 Created    : ${c201}    (allowed within limit)
  429 RATE_429   : ${c429}    (rate-limited)
  other          : ${cOther}   (must be < 10)
  Retry-After header set: ${cRetry}  (must be > 0 when 429 present)

정책 확인 (ADR-0017 § "Rate Limit Policy"):
  - 한도: 100 req/s = 6000 RPM
  - 429 응답: RATE_429 + Retry-After 헤더 필수
  - 결과 카드: docs/optimizations/_evidence/ 에 첨부 후 LT-01 카드 §"부수" 인용
============================================================
`;
  return {
    stdout: summary,
    'lt02-summary.json': JSON.stringify(data),
  };
}
