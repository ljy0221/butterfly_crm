#!/usr/bin/env bash
# S14P31B101-294 Phase A — Baseline 측정 실행 통합 스크립트
#
# 전제조건:
# 1) docker compose 인프라 기동:
#    docker compose -f docker-compose-local.yml -f docker-compose-observability.yml up -d
# 2) Spring app 부팅: ./gradlew :butterfly-api:bootRun
# 3) 사전 데이터 준비 (수동 또는 API 호출):
#    - 테스트 계정 + 프로젝트 + 세그먼트 + 메시지 템플릿
#    - DRAFT 상태 캠페인 충분히 (시나리오 B 용 100개 + E 용 30개)
# 4) test-data.env 파일 작성 후 본 스크립트 실행
#
# 출력: docs/portfolio-graphs/baseline-results/ 안에 raw JSON/CSV/summary
#       docs/portfolio-graphs/snapshots/baseline-prom-*.json 안에 Prometheus 시계열 raw

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# test-data.env 로드 (BASE_URL, JWT, PROJECT_ID, CAMPAIGN_IDS, LATENCY_CAMPAIGN_IDS)
if [ -f "$SCRIPT_DIR/test-data.env" ]; then
  set -a
  source "$SCRIPT_DIR/test-data.env"
  set +a
else
  echo "[ERROR] $SCRIPT_DIR/test-data.env 를 먼저 작성하세요. test-data.env.example 참고."
  exit 1
fi

OUTPUT_DIR="$REPO_ROOT/docs/portfolio-graphs/baseline-results"
SNAPSHOT_DIR="$REPO_ROOT/docs/portfolio-graphs/snapshots"
mkdir -p "$OUTPUT_DIR" "$SNAPSHOT_DIR"

TIMESTAMP=$(date +%Y%m%d-%H%M%S)
RUN_START_TS=$(date +%s)

echo "[1/3] K6 baseline 시나리오 실행 (B + E)"
docker compose \
  -f "$REPO_ROOT/docker-compose-local.yml" \
  -f "$REPO_ROOT/docker-compose-observability.yml" \
  run --rm \
  -e BASE_URL="${BASE_URL:-http://host.docker.internal:8080}" \
  -e JWT="$JWT" \
  -e PROJECT_ID="$PROJECT_ID" \
  -e CAMPAIGN_IDS="$CAMPAIGN_IDS" \
  -e LATENCY_CAMPAIGN_IDS="$LATENCY_CAMPAIGN_IDS" \
  k6 run \
    --out json=/output/baseline-results/baseline-raw-${TIMESTAMP}.json \
    --out csv=/output/baseline-results/baseline-raw-${TIMESTAMP}.csv \
    /scripts/k6-baseline-scenarios.js

RUN_END_TS=$(date +%s)
echo ""
echo "[2/3] Prometheus 시계열 raw 추출 (start=$RUN_START_TS, end=$RUN_END_TS)"
"$SCRIPT_DIR/snapshot-prom.sh" "$RUN_START_TS" "$RUN_END_TS" "baseline-${TIMESTAMP}"

echo ""
echo "[3/3] 그래프 자동 렌더 fallback (CSV → PNG)"
python3 "$SCRIPT_DIR/render-graphs.py" \
  --input "$OUTPUT_DIR/baseline-raw-${TIMESTAMP}.csv" \
  --output "$REPO_ROOT/docs/portfolio-graphs/images/auto-baseline-${TIMESTAMP}" \
  --label "Baseline (BEFORE)" || echo "[WARN] render-graphs.py 실패 — matplotlib 의존성 확인"

echo ""
echo "✅ Baseline 측정 완료. 결과:"
echo "  - K6 raw:    $OUTPUT_DIR/baseline-raw-${TIMESTAMP}.{json,csv}"
echo "  - K6 summary: $OUTPUT_DIR/baseline-summary.json"
echo "  - Prom snapshot: $SNAPSHOT_DIR/baseline-${TIMESTAMP}-*.json"
echo "  - PNG fallback: $REPO_ROOT/docs/portfolio-graphs/images/auto-baseline-${TIMESTAMP}-*.png"
echo ""
echo "다음 단계: Grafana http://localhost:3000 에서 dashboard 확인 + 캡처 (CAPTURE-GUIDE.md)"
