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

# B2 안전 가드 — bcrypt 해시 placeholder 미교체 감지
if grep -q 'REPLACE_BCRYPT_HASH_FOR_demo1234' "${SCRIPT_DIR}/01-project-and-account.sql"; then
  echo "❌ ERROR: bcrypt 해시 placeholder가 교체되지 않았습니다."
  echo "   README 의 'bcrypt 해시 교체' 단계를 먼저 수행하세요:"
  echo "   1) htpasswd -bnBC 10 \"\" demo1234 | tr -d ':\\n' 결과 복사"
  echo "   2) 01-project-and-account.sql 의 \$2a\$10\$REPLACE... 라인을 그 결과로 교체"
  exit 1
fi

# ──────────────────────────────────────────────────────────────────────────────
# FCM credential JSON 치환 (firebase-service-account.json 존재 시)
# ──────────────────────────────────────────────────────────────────────────────
FCM_JSON_FILE="${SCRIPT_DIR}/firebase-service-account.json"
TEMPLATE="${SCRIPT_DIR}/04-fcm-credential.sql.template"
RENDERED="${SCRIPT_DIR}/.tmp-04-fcm-credential.sql"

FCM_READY=false
if [[ -f "$FCM_JSON_FILE" ]]; then
  if ! command -v python3 >/dev/null 2>&1; then
    echo "WARNING: python3 가 설치되어 있지 않습니다 — FCM credential 시드를 건너뜁니다."
  else
    # python3 으로 안전 치환 — JSON 의 \, &, ', " 모두 정상 처리
    # 1) 한 줄로 압축 (jq 가 있으면 사용, 없으면 그대로)
    # 2) Python 으로 template placeholder 치환 (sed/awk 의 특수문자 위험 회피)
    if command -v jq >/dev/null 2>&1; then
      FCM_JSON_INLINE=$(jq -c . "$FCM_JSON_FILE")
    else
      FCM_JSON_INLINE=$(tr -d '\n' < "$FCM_JSON_FILE")
    fi
    python3 - "$TEMPLATE" "$RENDERED" "$FCM_JSON_INLINE" <<'PY'
import sys, pathlib
template_path, rendered_path, payload = sys.argv[1], sys.argv[2], sys.argv[3]
content = pathlib.Path(template_path).read_text(encoding='utf-8')
sql_escaped = payload.replace("'", "''")
content = content.replace("__REPLACE_WITH_SERVICE_ACCOUNT_JSON__", sql_escaped)
pathlib.Path(rendered_path).write_text(content, encoding='utf-8')
PY
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
