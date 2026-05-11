// =============================================================================
// LT-01 — 이벤트 수집 API 부하 테스트
//
// 시나리오 (backend/docs/qa/03-load-and-failure-scenarios.md §2.1):
//   POST /api/v1/events/actions @ 100 req/s, 5분 지속
//
// 임계치 (ADR-0017 § "Consequences"):
//   - http_req_duration p99 < 200ms
//   - http_req_failed rate < 0.1%
//
// 실행:
//   k6 run -e API_KEY=pk_live_xxxx... -e LOG_ID=42 scripts/qa/k6/lt01-event-ingest.js
//
// CI 통합 (선택):
//   k6 run --out json=lt01.json scripts/qa/k6/lt01-event-ingest.js
//   → lt01.json 을 docs/optimizations/_evidence/ 에 박제
//
// 포트폴리오 카드:
//   docs/optimizations/2026-05-11_event-ingest-api-p99.md (BEFORE-ONLY)
//   측정 후 Result 섹션 채워 COMPLETE 승격.
// =============================================================================

import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL, buildActionPayload, authHeaders } from './common.js';

export const options = {
  scenarios: {
    sustained_100rps: {
      executor: 'constant-arrival-rate',
      rate: 100,
      timeUnit: '1s',
      duration: '5m',
      preAllocatedVUs: 50,
      maxVUs: 200,
    },
  },
  thresholds: {
    // ADR-0017 명세
    http_req_duration: ['p(99)<200', 'p(95)<100'],
    http_req_failed: ['rate<0.001'],
    // 부수 추적
    'http_req_duration{expected:200}': ['p(99)<200'],
    checks: ['rate>0.999'],
  },
  // 출력에 추적 가능한 태그 부여
  tags: {
    scenario: 'LT-01',
    target: 'event-ingest-api',
  },
};

export default function () {
  const payload = buildActionPayload();
  const res = http.post(`${BASE_URL}/api/v1/events/actions`, payload, {
    headers: authHeaders(),
    tags: { expected: '200' }, // threshold 분리용
  });

  check(res, {
    'status is 201': (r) => r.status === 201,
    'has body.success=true': (r) => {
      try {
        return r.json('success') === true;
      } catch (_) {
        return false;
      }
    },
  });
}

// 시나리오 종료 시 요약을 한 곳에 출력 — Result 섹션 채울 때 인용.
export function handleSummary(data) {
  const m = data.metrics;
  const p50 = m.http_req_duration?.values['p(50)']?.toFixed(2);
  const p95 = m.http_req_duration?.values['p(95)']?.toFixed(2);
  const p99 = m.http_req_duration?.values['p(99)']?.toFixed(2);
  const failRate = ((m.http_req_failed?.values?.rate ?? 0) * 100).toFixed(3);
  const reqs = m.http_reqs?.values?.count;

  const summary = `
============================================================
LT-01 — Event Ingest API @ 100 req/s for 5m
============================================================
Total requests      : ${reqs}
Failure rate        : ${failRate}%        (limit: < 0.1%)
http_req_duration   :
  p50               : ${p50} ms
  p95               : ${p95} ms            (limit: < 100ms)
  p99               : ${p99} ms            (limit: < 200ms ADR-0017)

→ docs/optimizations/2026-05-11_event-ingest-api-p99.md
  의 Result 섹션에 인용 + JSON 첨부:
  k6 run --summary-export=lt01-summary.json --out json=lt01-raw.json scripts/qa/k6/lt01-event-ingest.js
============================================================
`;
  return {
    stdout: summary,
    'lt01-summary.json': JSON.stringify(data),
  };
}
