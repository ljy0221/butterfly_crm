# Campaign Loadtest Toolkit (S14P31B101-299)

운영 환경(`https://k14b101.p.ssafy.io`) 에서 캠페인 발송 파이프라인이 200건을 정상 처리하는지 e2e 로 검증한다. 그 중 실 디바이스 2대(에뮬레이터 5556 / 5558)는 푸시 수신까지 확인한다.

> ⚠️ 운영 DB 에 시드 데이터를 INSERT 한다. cleanup 까지 반드시 수행할 것.
> 모든 row 는 prefix `loadtest-` / name `LoadTest-*` 로 식별 가능하며, 운영 데이터와 ID 충돌 없음.

---

## 0. 파이프라인 요약

```
trigger-campaign.ps1 (local)
        ↓ PATCH .../campaigns/{id}/status {status: ACTIVE}
butterfly-api  ──► Kafka  campaign.send.requested
                            ↓
butterfly-worker ── SegmentMemberLookupAdapter → user 200건
                  → DeliveryCommand 200건 fan-out
                            ↓
                 Kafka  delivery-requests (200 partitions key=projectId)
                            ↓
butterfly-notification ── FCM Admin SDK → 발송
                  → notification_logs 200 row INSERT
                  → Kafka notification-send-result
                            ↓
butterfly-reporting (stats 집계)
```

검증해야 할 것:
| 무엇 | 어디서 | 기대값 |
|---|---|---|
| 시드 적재 | `verify.sql` §1 | users 200 / devices 200 / segment_members 200 |
| 캠페인 상태 전이 | `verify.sql` §2 | campaign_status = ACTIVE → ENDED, execution status = SUCCESS |
| 발송 로그 적재 | `verify.sql` §3 | 합계 200 |
| 실 토큰 결과 | `verify.sql` §4, adb logcat | loadtest-dev-0001/0002 가 SENT/DELIVERED + 에뮬에서 푸시 수신 |
| 가짜 토큰 결과 | `verify.sql` §5 | FAILED 198, error_code = `UNREGISTERED` / `INVALID_ARGUMENT` |
| Kafka lag | K3s 마스터 | 종료 시 0 |

---

## 1. 사전 준비

### 1-1. 운영 환경의 본인 값 확인 (4개)

`leeseoyeon10116@gmail.com` 으로 운영 환경에 로그인한 뒤, 다음 4개 값을 본인 환경에서 직접 확인한다.

| 값 | 어디서 | 비고 |
|---|---|---|
| `PROJECT_ID` | 프로젝트 화면 URL / DB `projects` | leeseoyeon10116 본인 프로젝트 |
| `MEMBER_ID` | DB `members WHERE email='leeseoyeon10116@gmail.com'` | OWNER member |
| `BUTTERFLY_PASSWORD` (env) | 본인이 기억 | `802025ym12!@` |
| FCM credential | `channel_credentials WHERE project_id=PROJECT_ID AND channel='PUSH'` | **이미 등록되어 있어야 한다**. 없으면 운영 UI / `demo-seed` 의 04 단계로 먼저 등록 |

### 1-2. 에뮬레이터 토큰 유효성 (선택)

스크립트 [01-loadtest-users-devices.sql](01-loadtest-users-devices.sql) 의 35-39 / 42-46 행에 박힌 5558 / 5556 토큰이 만료되었으면 발송이 모두 FAILED 가 된다. 의심되면 에뮬레이터에서 새 토큰을 발급받아 해당 라인 두 곳을 갱신.

---

## 2. 시드 적재 (K3s 마스터에서 실행)

bastion → K3s 마스터 2단계 SSH 후 `mysql-0` pod 안에서 직접 실행한다.

```bash
# 0) bastion → K3s 마스터
ssh -i K14B101T.pem ubuntu@k14b101.p.ssafy.io
ssh -i ~/butterfly-key.pem ubuntu@10.0.1.144

# 1) 이 디렉터리를 mysql-0 pod 에 복사
git clone <repo> butterfly  # 또는 git pull 로 최신화
cd butterfly/backend/scripts/loadtest

kubectl cp -n butterfly 01-loadtest-users-devices.sql mysql-0:/tmp/01.sql
kubectl cp -n butterfly 02-loadtest-segment.sql       mysql-0:/tmp/02.sql
kubectl cp -n butterfly 03-loadtest-campaign.sql      mysql-0:/tmp/03.sql

# 2) placeholder 치환하고 mysql 실행 — 본인 PROJECT_ID / MEMBER_ID 채우기
ROOT_PW=$(kubectl get secret mysql-secret -n butterfly -o jsonpath='{.data.root-password}' | base64 -d)
PID=<YOUR_PROJECT_ID>
MID=<YOUR_MEMBER_ID>

for f in 01 02 03; do
  kubectl exec -i -n butterfly mysql-0 -- bash -c "
    sed -e 's/\${PROJECT_ID}/$PID/g' -e 's/\${MEMBER_ID}/$MID/g' /tmp/${f}.sql \
      | mysql -uroot -p'$ROOT_PW' --default-character-set=utf8mb4 butterfly
  "
done
```

각 SQL 끝의 `SELECT ... AS ...` 로 적재 카운트와 생성된 `segment_id` / `campaign_id` 가 출력된다. **`03` 의 출력에서 `campaign_id` 를 메모해두라** — 트리거 스크립트에 인자로 전달한다.

> 로컬 docker-compose 환경에서 dry-run 하려면 `seed.sh` 를 그대로 사용:
> ```powershell
> $env:MYSQL_PASSWORD = '<localdev-password>'
> $env:LOADTEST_PROJECT_ID = '1'
> $env:LOADTEST_MEMBER_ID  = '1'
> bash seed.sh
> ```

---

## 3. Kafka lag / worker 로그 모니터링 (별도 터미널 2개)

시드 직후 → 트리거 직전에 K3s 마스터에서 두 터미널을 띄워두면 발송 진행이 실시간으로 보인다.

```bash
# 터미널 A — campaign.send.requested → delivery-requests 컨슈머 그룹 lag
kubectl exec -n butterfly kafka-0 -- bash -c '
  while true; do clear;
    /opt/bitnami/kafka/bin/kafka-consumer-groups.sh \
      --describe --all-groups \
      --bootstrap-server localhost:9092 2>/dev/null \
      | grep -E "campaign|delivery|notification" || true
    sleep 2
  done
'

# 터미널 B — worker 의 DeliveryKafkaConsumer 처리 로그
WORKER_POD=$(kubectl get pod -n butterfly -l app=butterfly-worker -o jsonpath='{.items[0].metadata.name}')
kubectl logs -n butterfly $WORKER_POD -f --tail=20 \
  | grep --line-buffered -iE 'DeliveryKafkaConsumer|CampaignSendInstruction|FcmService|notification_logs'
```

---

## 4. 트리거 (로컬 PowerShell)

```powershell
# 비밀번호는 환경변수로
$env:BUTTERFLY_PASSWORD = '802025ym12!@'

# 03 SQL 출력에서 메모해둔 campaign_id 사용
.\trigger-campaign.ps1 -ProjectId <PID> -CampaignId <CID> -PollSeconds 90
```

스크립트가 출력하는 흐름:
1. `accessToken 획득` — 로그인 성공
2. `Kafka campaign.send.requested 발행 후 worker 가 200건 fan-out 시작 예상` — PATCH 200 OK
3. `+ Ns notification_logs total = N` — 카운트가 0 → ... → 200 으로 차오르는 동안 진행 표시

만약 PATCH 가 4xx 면:
- 401 — 토큰 만료 / 잘못된 비밀번호 → BUTTERFLY_PASSWORD 재확인
- 403 — `@PreAuthorize` 실패 → MEMBER_ID 가 그 프로젝트의 OWNER/EDITOR 인지 확인
- 429 — PATCH campaign status 는 10 RPM 제한, 1분 후 재시도

---

## 5. 검증

### 5-1. DB 측 (K3s 마스터)

```bash
kubectl cp -n butterfly verify.sql mysql-0:/tmp/verify.sql

kubectl exec -i -n butterfly mysql-0 -- bash -c "
  sed 's/\${PROJECT_ID}/$PID/g' /tmp/verify.sql \
    | mysql -uroot -p'$ROOT_PW' butterfly
"
```

기대 출력:
- §1 seed counts → `200 / 200 / 200`
- §2 campaign → `campaign_status = ACTIVE` (발송 진행 중) 또는 `ENDED` (완료 후)
- §3 notification_logs status → 합계 200, `SENT` + `DELIVERED` + `FAILED` 의 합
- §4 real device delivery → 2 row, status `SENT` 또는 `DELIVERED`
- §5 failure error codes → 198건 분포 (대부분 `UNREGISTERED` / `INVALID_ARGUMENT`)

### 5-2. 실 디바이스 수신 (로컬)

```powershell
# 에뮬레이터 5558
adb -s emulator-5558 logcat -d | Select-String -Pattern 'FCM_Main|FCM_Service|onMessageReceived' | Select-Object -Last 30

# 에뮬레이터 5556
adb -s emulator-5556 logcat -d | Select-String -Pattern 'FCM_Main|FCM_Service|onMessageReceived' | Select-Object -Last 30
```

`onMessageReceived` 라인에 `LoadTest Push` 가 들어와 있으면 실제 수신 확인.

문제 발생 시: GMS push channel 크래시 → 에뮬레이터 reboot, AUTHENTICATION_FAILED → Play Store 에서 ToS 동의 후 재시도.

---

## 6. cleanup (반드시 수행)

```bash
kubectl cp -n butterfly cleanup.sql mysql-0:/tmp/cleanup.sql
kubectl exec -i -n butterfly mysql-0 -- bash -c "
  sed 's/\${PROJECT_ID}/$PID/g' /tmp/cleanup.sql \
    | mysql -uroot -p'$ROOT_PW' butterfly
"
```

마지막 SELECT 가 모두 `0` 이면 깨끗하게 제거된 상태.

---

## 7. 잘 안 풀릴 때 — 가장 흔한 오류

| 증상 | 원인 | 해결 |
|---|---|---|
| `notification_logs` 가 0건 | segment_id 가 null 이거나 segment_members 가 비어있음 | `verify.sql` §1 / §2 확인. 시드 순서대로 다시 실행 |
| 200건 FAILED, error_code = `INVALID_ARGUMENT: registration token is not a valid` | 가짜 토큰만 발송됨 — 실 토큰 2건도 만료 | 에뮬레이터에서 새 토큰 발급해 SQL 36/45 행 갱신 |
| 200건 FAILED, error_code = `Channel credential not found` | `channel_credentials` 에 row 없음 | demo-seed `04-fcm-credential.sql.template` 로 등록 |
| 발송이 일부만 진행되고 멈춤 | DeliveryKafkaConsumer 가 `@Profile` 없이 등록되어 api/worker 양쪽이 동일 group 으로 partition 경쟁 가능 | worker pod 만 살아있는지 확인. api pod 의 consumer 동작 여부는 코드 확인 |
| 캠페인 ACTIVE 전환이 401 | prod 에서는 `LocalAuthFilter` (X-Member-Id 헤더 우회) 가 비활성 — JWT Bearer 필수 | `trigger-campaign.ps1` 의 1단계 로그인이 정상 종료되어야 함 |

---

## 8. 관련 자료

- Jira: [S14P31B101-299](https://ssafy.atlassian.net/browse/S14P31B101-299)
- 부하 시나리오 문서: `docs/qa/03-load-and-failure-scenarios.md`
- 시연용 시드: `backend/scripts/demo-seed/` (별도 목적 — 데모 데이터 vs 부하 검증)
