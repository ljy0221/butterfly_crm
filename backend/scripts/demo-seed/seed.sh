#!/usr/bin/env bash
# 5/22 시연 시드 실행 스크립트
# 사용법: MYSQL_PASSWORD=xxx bash seed.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# ──────────────────────────────────────────────────────────────────────────────
# 환경변수 (미설정 시 기본값 사용, MYSQL_PASSWORD 는 필수)
# ──────────────────────────────────────────────────────────────────────────────
: "${MYSQL_HOST:=localhost}"
: "${MYSQL_PORT:=3306}"
: "${MYSQL_USER:=butterfly}"
: "${MYSQL_PASSWORD:?MYSQL_PASSWORD 환경변수가 필요합니다}"
: "${MYSQL_DB:=butterfly}"

run_sql() {
  local file="$1"
  echo "==> $(basename "$file")"
  mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DB" < "$file"
}

# ──────────────────────────────────────────────────────────────────────────────
# FCM credential JSON 치환 (firebase-service-account.json 존재 시)
# ──────────────────────────────────────────────────────────────────────────────
FCM_JSON_FILE="${SCRIPT_DIR}/firebase-service-account.json"
TEMPLATE="${SCRIPT_DIR}/04-fcm-credential.sql.template"
RENDERED="${SCRIPT_DIR}/.tmp-04-fcm-credential.sql"

FCM_READY=false
if [[ -f "$FCM_JSON_FILE" ]]; then
  if ! command -v jq >/dev/null 2>&1; then
    echo "WARNING: jq 가 설치되어 있지 않습니다 — FCM credential 시드를 건너뜁니다."
    echo "         (jq 설치 후 재실행하거나 D-3 리허설에서 수동 적재하세요)"
  else
    FCM_JSON_INLINE=$(jq -c . "$FCM_JSON_FILE")
    # SQL 문자열 이스케이프: 작은따옴표 → ''
    FCM_JSON_ESC=$(printf '%s' "$FCM_JSON_INLINE" | sed "s/'/''/g")
    awk -v repl="$FCM_JSON_ESC" \
        '{gsub(/__REPLACE_WITH_SERVICE_ACCOUNT_JSON__/, repl)}1' \
        "$TEMPLATE" > "$RENDERED"
    FCM_READY=true
  fi
else
  echo "WARNING: ${FCM_JSON_FILE} 파일이 없습니다 — FCM credential 시드를 건너뜁니다."
  echo "         Service Account JSON 을 해당 경로에 두고 재실행하세요."
fi

# ──────────────────────────────────────────────────────────────────────────────
# SQL 순차 실행
# ──────────────────────────────────────────────────────────────────────────────
run_sql "${SCRIPT_DIR}/01-project-and-account.sql"
run_sql "${SCRIPT_DIR}/02-customers-and-events.sql"
run_sql "${SCRIPT_DIR}/03-historical-campaigns.sql"

if [[ "$FCM_READY" == true ]]; then
  run_sql "$RENDERED"
  rm -f "$RENDERED"
fi

echo ""
echo "시연 시드 완료."
echo "  project_id=1  /  member: demo@butterfly.io  /  password: (bcrypt 교체 필요)"
echo ""
echo "  [중요] 01-project-and-account.sql 의 bcrypt placeholder 를 실제 해시로 교체했는지 확인하세요."
echo "  생성 명령: htpasswd -bnBC 10 \"\" demo1234 | tr -d ':\\n'"
