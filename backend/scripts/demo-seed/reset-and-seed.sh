#!/usr/bin/env bash
# 5/22 시연 데이터 완전 reset 후 재시드
# project_id=1 범위의 모든 시연 데이터를 삭제하고 seed.sh 를 재실행합니다.
# 사용법: MYSQL_PASSWORD=xxx bash reset-and-seed.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo ""
echo "============================================================"
echo " WARNING: project_id=1 의 시연 데이터를 완전히 reset 합니다."
echo "          이 작업은 되돌릴 수 없습니다."
echo "============================================================"
echo ""
printf "계속하시겠습니까? (y/N) "
read -r ans
[[ "$ans" =~ ^[Yy]$ ]] || { echo "취소됨."; exit 0; }

echo ""
bash "${SCRIPT_DIR}/seed.sh"
echo ""
echo "reset + seed 완료."
