#!/usr/bin/env bash
# Loadtest seed — user/device/segment_member 200건 + 캠페인 1건 (DRAFT)
# 사용법:
#   MYSQL_PASSWORD=xxx LOADTEST_PROJECT_ID=N LOADTEST_MEMBER_ID=M bash seed.sh
#
# 실행 위치:
#   - 로컬 (docker-compose) → MYSQL_HOST=localhost
#   - K3s 마스터 → kubectl exec 안에서 직접 mysql 실행하는 패턴 권장 (README 참조)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

: "${MYSQL_HOST:=localhost}"
: "${MYSQL_PORT:=3306}"
: "${MYSQL_USER:=butterfly}"
: "${MYSQL_PASSWORD:?MYSQL_PASSWORD 환경변수가 필요합니다}"
: "${MYSQL_DB:=butterfly}"
: "${LOADTEST_PROJECT_ID:?LOADTEST_PROJECT_ID 환경변수가 필요합니다 (운영 환경의 본인 프로젝트 ID)}"
: "${LOADTEST_MEMBER_ID:?LOADTEST_MEMBER_ID 환경변수가 필요합니다 (운영 환경의 본인 OWNER member ID)}"

render_and_run() {
  local file="$1"
  echo "==> $(basename "$file")"
  sed -e "s/\${PROJECT_ID}/${LOADTEST_PROJECT_ID}/g" \
      -e "s/\${MEMBER_ID}/${LOADTEST_MEMBER_ID}/g" \
      "$file" \
    | mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" -p"$MYSQL_PASSWORD" \
            --default-character-set=utf8mb4 "$MYSQL_DB"
}

render_and_run "${SCRIPT_DIR}/01-loadtest-users-devices.sql"
render_and_run "${SCRIPT_DIR}/02-loadtest-segment.sql"
render_and_run "${SCRIPT_DIR}/03-loadtest-campaign.sql"

echo ""
echo "Loadtest 시드 완료."
echo "  - project_id     : ${LOADTEST_PROJECT_ID}"
echo "  - users / devices: 200 (loadtest-user-0001 ~ 0200)"
echo "  - segment        : LoadTest-Segment (READY)"
echo "  - campaign       : LoadTest-Campaign-200 (DRAFT, IMMEDIATE)"
echo ""
echo "다음 단계:"
echo "  1) (로컬 PowerShell) trigger-campaign.ps1 실행 → DRAFT → ACTIVE 전환 + stats poll"
echo "  2) (K3s 마스터)      verify.sql 로 notification_logs / Kafka lag 검증"
echo "  3) (에뮬레이터)      adb logcat | grep FCM_Main 으로 5556/5558 수신 확인"
