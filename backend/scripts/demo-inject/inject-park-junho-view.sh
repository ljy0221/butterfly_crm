#!/usr/bin/env bash
# 5/22 시연 5.6 단계 백업 스크립트
# Android 앱 실패 시 박준호(user-142) 의 운동화 product_view 이벤트를 직접 주입합니다.
#
# 사전 준비:
#   cp .env.example .env  →  API_BASE / PROJECT_ID 값 확인
#
# 사용법:
#   bash inject-park-junho-view.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# .env 로드 (존재 시)
if [[ -f "${SCRIPT_DIR}/.env" ]]; then
  set -a
  # shellcheck source=/dev/null
  source "${SCRIPT_DIR}/.env"
  set +a
fi

: "${API_BASE:?API_BASE 환경변수가 필요합니다 (.env 또는 export)}"
: "${PROJECT_ID:?PROJECT_ID 환경변수가 필요합니다}"

# 백엔드는 Asia/Seoul LocalDateTime — UTC 가 아닌 로컬 시간 사용
OCCURRED_AT="$(date +"%Y-%m-%dT%H:%M:%S")"
EVENT_UUID="$(uuidgen 2>/dev/null | tr '[:upper:]' '[:lower:]' || python3 -c 'import uuid; print(uuid.uuid4())')"

echo "==> 박준호 운동화 product_view 이벤트 주입"
echo "    API_BASE   : ${API_BASE}"
echo "    PROJECT_ID : ${PROJECT_ID}"
echo "    occurred_at: ${OCCURRED_AT}"
echo ""

HTTP_CODE=$(curl -sS \
  -o /tmp/inject-response.json \
  -w "%{http_code}" \
  -X POST "${API_BASE}/api/v1/events" \
  -H "Content-Type: application/json" \
  -H "X-Project-Id: ${PROJECT_ID}" \
  --data @- <<JSON
{
  "userId": "user-142",
  "deviceId": "dev-fallback-142",
  "eventCategory": "product_view",
  "occurredAt": "${OCCURRED_AT}",
  "eventData": {
    "category": "운동화",
    "productId": "sku-9847",
    "productName": "데일리 러닝화"
  }
}
JSON
)

if [[ "${HTTP_CODE}" =~ ^2 ]]; then
  echo "주입 성공 (HTTP ${HTTP_CODE})"
  cat /tmp/inject-response.json
  echo ""
  echo "20~30초 후 브라우저 세그먼트 빌더에서 '미리보기 실행' 을 클릭하세요."
else
  echo "주입 실패 (HTTP ${HTTP_CODE})"
  cat /tmp/inject-response.json
  echo ""
  echo "문제 해결:"
  echo "  HTTP 4xx → .env 의 PROJECT_ID / API_BASE 확인"
  echo "  HTTP 5xx → 백엔드 로그 확인 (docker logs butterfly-api)"
  exit 1
fi
