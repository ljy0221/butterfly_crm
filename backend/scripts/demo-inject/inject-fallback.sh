#!/usr/bin/env bash
# 최소 백업 — .env 분실 / 환경변수 오류 시 즉시 실행
# 하드코딩 값으로 박준호 product_view 이벤트를 주입합니다.
# API_BASE 가 다르면 URL 을 직접 수정하세요.
set -euo pipefail

# 백엔드는 Asia/Seoul LocalDateTime — 호스트 TZ 무관하게 KST 고정
curl -sS \
  -X POST http://localhost:8080/api/v1/events \
  -H "Content-Type: application/json" \
  -H "X-Project-Id: 1" \
  -d "{
    \"userId\": \"user-142\",
    \"deviceId\": \"dev-user-142-placeholder\",
    \"eventCategory\": \"product_view\",
    \"occurredAt\": \"$(TZ=Asia/Seoul date +"%Y-%m-%dT%H:%M:%S")\",
    \"eventData\": {\"category\": \"운동화\", \"productId\": \"sku-9847\"}
  }"
echo
