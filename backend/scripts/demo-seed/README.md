# Demo Seed Scripts (5/22 시연용)

`project_id=1` 범위의 시연 데이터를 MySQL 에 적재합니다.

---

## 파일 구성

| 파일 | 설명 |
|------|------|
| `01-project-and-account.sql` | 프로젝트, 운영자 계정, 메시지 템플릿 |
| `02-customers-and-events.sql` | 페르소나 2명(이지은/박준호) + 노이즈 10명 + 이벤트 이력 |
| `03-historical-campaigns.sql` | ENDED 캠페인 6건 + 실행 이력 |
| `04-fcm-credential.sql.template` | Firebase FCM credential (seed.sh 가 JSON 치환 후 실행) |
| `seed.sh` | 위 파일을 순서대로 실행 |
| `reset-and-seed.sh` | 확인 프롬프트 후 seed.sh 실행 |

---

## 사전 준비

### 1. bcrypt 해시 교체 (필수)

`01-project-and-account.sql` 에 포함된 아래 placeholder 를 실제 해시로 교체해야 합니다.

```
$2a$10$REPLACE_BCRYPT_HASH_FOR_demo1234
```

해시 생성 방법 (Linux/macOS):

```bash
htpasswd -bnBC 10 "" demo1234 | tr -d ':\n'
```

출력된 문자열을 `01-project-and-account.sql` 의 `password_hash` 값으로 교체하세요.

### 2. FCM Service Account JSON 준비 (선택 — 푸시 발송 필요 시)

Firebase Console → 프로젝트 설정 → 서비스 계정 → 새 비공개 키 생성 후
파일명을 `firebase-service-account.json` 으로 변경하여 이 디렉터리에 놓으세요.

```
backend/scripts/demo-seed/firebase-service-account.json   ← gitignore 처리됨
```

`jq` 가 설치되어 있어야 JSON 인라인 변환이 가능합니다.

```bash
# jq 설치 (Ubuntu/Debian)
sudo apt-get install -y jq

# macOS
brew install jq
```

FCM JSON 이 없으면 `seed.sh` 가 경고를 출력하고 나머지 시드는 정상 실행됩니다.

---

## 실행

```bash
# 환경변수 설정
export MYSQL_HOST=localhost       # 기본값
export MYSQL_PORT=3306            # 기본값
export MYSQL_USER=butterfly       # 기본값
export MYSQL_DB=butterfly         # 기본값
export MYSQL_PASSWORD=<비밀번호>  # 필수

# 최초 시드
bash seed.sh

# 재시드 (데이터 초기화 후 재적재)
bash reset-and-seed.sh
```

---

## 멱등성

각 SQL 파일은 `DELETE WHERE project_id=1` → `INSERT` 순서로 실행되므로
여러 번 실행해도 동일한 결과를 보장합니다.

`SET FOREIGN_KEY_CHECKS = 0 / 1` 으로 FK 순서 제약을 우회합니다.

---

## D-Day 체크리스트

| 시점 | 작업 |
|------|------|
| D-3 리허설 | `reset-and-seed.sh` 전체 실행 → 세그먼트 빌드 → 캠페인 발송 확인 |
| D-1 | `reset-and-seed.sh` 재실행 → 박준호 device 등록 (Android 앱) 확인 |
| D-day 시연 1시간 전 | `reset-and-seed.sh` 재실행 → 백엔드/프론트 기동 확인 |
| 시연 5.6 단계 | 박준호 Android 앱 product_view OR `demo-inject/inject-park-junho-view.sh` |

---

## 알려진 한계

- `notification_logs`, `campaign_stats` 테이블은 이 시드에 포함되지 않습니다.
  대시보드의 일부 집계 그래프(채널별 발송 성공률 등)는 데이터가 희박할 수 있습니다.
  D-3 리허설에서 확인 후 필요 시 추가 시드를 작성하세요.

- 박준호 이벤트의 `device_id=730` (이지은 디바이스 임시 사용)은 실기기 등록 후
  실제 device_id 로 자동 갱신되지 않습니다. 시연 중 앱 이벤트가 새로운 device_id 로
  적재되면 세그먼트 카운트에 정상 반영됩니다.
