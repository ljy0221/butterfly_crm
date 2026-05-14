// S14P31B101-294 Phase A — Baseline 부하 시나리오
//
// 시나리오 B (Throughput): 짧은 시간 N 캠페인 동시 활성화 (burst)
// 시나리오 E (Latency):     단일 VU 로 sequential activate, per-instruction 측정
//
// 시나리오 C (Resilience) 는 Phase C 에서 별도 작성 (장애 주입 인프라 필요)
//
// 사용:
//   docker compose -f docker-compose-local.yml -f docker-compose-observability.yml run --rm \
//     -e BASE_URL=http://host.docker.internal:8080 \
//     -e JWT=$JWT \
//     -e PROJECT_ID=$PROJECT_ID \
//     -e CAMPAIGN_IDS=$CAMPAIGN_IDS \
//     k6 run \
//       --out json=/output/baseline-results/baseline-raw.json \
//       --out csv=/output/baseline-results/baseline-raw.csv \
//       --summary-export=/output/baseline-results/baseline-summary.json \
//       /scripts/k6-baseline-scenarios.js
//
// 환경변수:
//   BASE_URL      Spring app endpoint (default: http://host.docker.internal:8080)
//   JWT           로그인 후 발급된 accessToken
//   PROJECT_ID    측정 대상 프로젝트 ID
//   CAMPAIGN_IDS  DRAFT 상태 캠페인 ID 콤마 구분 (시나리오 B 용 — 충분히 많이 준비)
//   LATENCY_CAMPAIGN_IDS  시나리오 E 용 별도 DRAFT 캠페인 ID 콤마 구분 (B 와 겹치면 안 됨)

import http from 'k6/http';
import { check } from 'k6';
import { Trend, Counter } from 'k6/metrics';
import exec from 'k6/execution';                                   // unique iteration index

const BASE_URL = __ENV.BASE_URL || 'http://host.docker.internal:8080';
const JWT = __ENV.JWT || '';
const PROJECT_ID = __ENV.PROJECT_ID || '1';
const CAMPAIGN_IDS = (__ENV.CAMPAIGN_IDS || '').split(',').filter(Boolean);
const LATENCY_CAMPAIGN_IDS = (__ENV.LATENCY_CAMPAIGN_IDS || '').split(',').filter(Boolean);

// custom metrics — JSON/CSV 결과에 추가 박제됨.
const activateLatency = new Trend('campaign_activate_latency_ms', true);  // HTTP duration
const successCount = new Counter('activate_success');
const failureCount = new Counter('activate_failure');

export const options = {
  scenarios: {
    // 시나리오 B — Throughput (burst activate)
    throughput_burst: {
      executor: 'shared-iterations',
      vus: 50,
      iterations: CAMPAIGN_IDS.length || 100,
      maxDuration: '120s',
      tags: { scenario: 'B-throughput' },
      exec: 'activateThroughput',
    },
    // 시나리오 E — Latency (single VU sequential, B 끝나고 시작)
    latency_baseline: {
      executor: 'per-vu-iterations',
      vus: 1,
      iterations: LATENCY_CAMPAIGN_IDS.length || 30,
      maxDuration: '120s',
      startTime: '130s',                            // B 종료 후 시작 (gap=10s)
      tags: { scenario: 'E-latency' },
      exec: 'activateLatencyOne',
    },
  },
  thresholds: {
    'http_req_duration{scenario:B-throughput}': ['p(95)<5000'],
    'http_req_failed{scenario:B-throughput}': ['rate<0.05'],
    'http_req_duration{scenario:E-latency}': ['p(95)<2000'],
    'http_req_failed{scenario:E-latency}': ['rate<0.01'],
  },
};

function activate(campaignId, scenarioTag) {
  const url = `${BASE_URL}/api/v1/projects/${PROJECT_ID}/campaigns/${campaignId}/status`;
  const payload = JSON.stringify({ status: 'ACTIVE' });
  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${JWT}`,
    },
    tags: { scenario: scenarioTag },
  };

  const res = http.patch(url, payload, params);
  activateLatency.add(res.timings.duration, { scenario: scenarioTag });

  const ok = check(res, {
    'http 200/201/204': (r) => [200, 201, 204].includes(r.status),
  });

  if (ok) successCount.add(1, { scenario: scenarioTag });
  else failureCount.add(1, { scenario: scenarioTag });

  return res;
}

export function activateThroughput() {
  // exec.scenario.iterationInTest 는 scenario 내에서 unique sequential index — VU 간 충돌 없음.
  // __ITER 는 VU 의 local count (shared-iterations 에서 VU 마다 0 부터 시작 → 같은 campaign 충돌).
  const idx = exec.scenario.iterationInTest % CAMPAIGN_IDS.length;
  activate(CAMPAIGN_IDS[idx], 'B-throughput');
}

export function activateLatencyOne() {
  const idx = exec.scenario.iterationInTest % LATENCY_CAMPAIGN_IDS.length;
  activate(LATENCY_CAMPAIGN_IDS[idx], 'E-latency');
}

export function handleSummary(data) {
  return {
    'stdout': textSummary(data),
    '/output/baseline-results/baseline-summary.json': JSON.stringify(data, null, 2),
  };
}

// k6 의 기본 summary 포맷 (minimal 재구현 — 외부 import 없이)
function textSummary(data) {
  const lines = [];
  lines.push('');
  lines.push('=== S14P31B101-294 Phase A — Baseline 측정 결과 ===');
  lines.push('');
  for (const [name, metric] of Object.entries(data.metrics)) {
    if (metric.values) {
      const vals = Object.entries(metric.values)
        .map(([k, v]) => `${k}=${typeof v === 'number' ? v.toFixed(2) : v}`)
        .join(' ');
      lines.push(`${name.padEnd(50)} ${vals}`);
    }
  }
  return lines.join('\n') + '\n';
}
