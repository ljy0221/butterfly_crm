#!/usr/bin/env bash
# bash 3.2 (macOS default) 호환 — associative array 사용 안 함.
# S14P31B101-294 — Prometheus 시계열 raw 데이터 추출
# 그래프가 깨져도 raw JSON 만 보존되면 100% 복원 가능.
#
# 사용: snapshot-prom.sh <start_ts> <end_ts> <label>
# 예:   snapshot-prom.sh 1715680000 1715680300 baseline-20260514-153000

set -euo pipefail

START_TS="${1:?start_ts (unix seconds) 필수}"
END_TS="${2:?end_ts (unix seconds) 필수}"
LABEL="${3:?label 필수}"
PROM_URL="${PROM_URL:-http://localhost:9090}"
STEP="${STEP:-5}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
PORTFOLIO_DIR="${PORTFOLIO_GRAPHS_DIR:-/Users/sylee/Downloads/S14P31B101-Campaign-CRUD-Send/portfolio-graphs}"
OUT_DIR="$PORTFOLIO_DIR/snapshots"
mkdir -p "$OUT_DIR"

# 측정 핵심 query 들 — name|query 한 쌍씩 정의 (bash 3.2 호환).
QUERY_PAIRS=(
  "kafka_listener_rate|rate(spring_kafka_listener_seconds_count[10s])"
  "kafka_listener_p95|histogram_quantile(0.95, sum(rate(spring_kafka_listener_seconds_bucket[1m])) by (le))"
  "kafka_listener_p99|histogram_quantile(0.99, sum(rate(spring_kafka_listener_seconds_bucket[1m])) by (le))"
  "kafka_listener_error_rate|rate(spring_kafka_listener_seconds_count{result=\"failure\"}[10s])"
  "kafka_template_p95|histogram_quantile(0.95, sum(rate(spring_kafka_template_seconds_bucket[1m])) by (le))"
  "http_activate_rate|rate(http_server_requests_seconds_count{uri=~\".*campaigns.*status.*\"}[10s])"
  "http_activate_p95|histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{uri=~\".*campaigns.*status.*\"}[1m])) by (le))"
  "process_cpu|process_cpu_usage"
  "jvm_memory_used|jvm_memory_used_bytes{area=\"heap\"}"
)

echo "Prometheus snapshot: start=$START_TS end=$END_TS label=$LABEL step=${STEP}s"

for pair in "${QUERY_PAIRS[@]}"; do
  name="${pair%%|*}"
  query="${pair#*|}"
  out_file="$OUT_DIR/${LABEL}-${name}.json"
  echo "  - $name → $out_file"
  curl -sG "$PROM_URL/api/v1/query_range" \
    --data-urlencode "query=$query" \
    --data-urlencode "start=$START_TS" \
    --data-urlencode "end=$END_TS" \
    --data-urlencode "step=${STEP}s" \
    > "$out_file"
done

echo "✅ snapshot 완료. raw 파일: $OUT_DIR/${LABEL}-*.json"
