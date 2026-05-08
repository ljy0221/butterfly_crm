# MVP 통합 테스트 시나리오 (4개 핵심 흐름)

> Owner: 이재영
> 최초 작성: 2026-05-08
> 관련 Jira: `S14P31B101-67`
> 관련 문서: [00-mvp-qa-baseline.md](./00-mvp-qa-baseline.md), [02-demo-checklist.md](./02-demo-checklist.md)

본 문서는 6개 도메인이 하나의 흐름으로 끊기지 않고 동작하는지 검증하는 **End-to-End 시나리오**다. 시나리오 1~4를 모두 통과하면 MVP 데모가 가능한 상태로 본다.

각 시나리오는 다음 구성:
- **목적**: 어떤 통합 경계를 검증하는가
- **선행 조건**: 데이터 시드, 토큰, 외부 의존
- **단계**: 도메인을 가로지르는 실행 순서 (각 단계마다 책임 도메인 명시)
- **기대 결과**: 통과 판정 기준
- **실패 시 진단**: 어디가 끊긴 건지 빠르게 좁히는 가이드

---

## 시나리오 #1 — 로그인 → 프로젝트 전환 → 권한 검증

### 목적
인증/테넌트 도메인이 정상 동작하고, 모든 API가 `project_id` 기반 권한을 강제하는지 검증.
**다른 프로젝트 데이터가 절대 노출되지 않음**을 확인한다 (P0 보안 기준).

### 선행 조건
- 시드 데이터: 프로젝트 2개 (Project A: id=1, Project B: id=2), 각 프로젝트에 OWNER/OPERATOR/ANALYST 멤버
- 멤버 X는 Project A에만 소속, 멤버 Y는 Project B에만 소속

### 단계

| # | 액션 | 책임 도메인 | 기대 |
|---|---|---|---|
| 1 | `POST /api/v1/auth/login` (멤버 X 자격 증명) | auth | 200, JWT 발급 (memberId, projectId=1, role=OPERATOR claim) |
| 2 | `GET /api/v1/projects/me` (X 토큰) | tenant | 200, Project A 정보 반환 |
| 3 | `GET /api/v1/projects/2` (X 토큰, B를 조회 시도) | tenant + auth | **403 ACCESS_DENIED** |
| 4 | `GET /api/v1/segments?projectId=2` (X 토큰) | segment + auth | **403** (다른 프로젝트 세그먼트 노출 차단) |
| 5 | `GET /api/v1/analytics/campaigns?projectId=1` (X 토큰) | analytics + auth | 200, Project A 데이터 |
| 6 | `GET /api/v1/analytics/campaigns?projectId=2` (X 토큰) | analytics + auth | **403** |
| 7 | 멤버 X 로그아웃 → 같은 토큰으로 다시 호출 | auth (Redis 블랙리스트) | **401 UNAUTHORIZED** |

### 기대 결과
- 모든 단계가 위 응답 코드와 일치
- 단계 7에서 블랙리스트가 즉시 반영됨 (지연 없음)
- audit_logs에 로그인/로그아웃 row 적재 (단계 1, 7)

### 실패 시 진단

| 증상 | 의심 도메인 | 확인 |
|---|---|---|
| 단계 3에서 200 응답 | auth (`@PreAuthorize` 미적용) 또는 tenant (controller 권한 가드 누락) | 해당 controller에 `@projectPermission` SpEL 적용 여부 |
| 단계 7에서 200 응답 | auth (Redis 블랙리스트 검사 실패) | `JwtAuthenticationFilter` 로그 확인 |
| 모든 401 → audit_logs 비어있음 | 감사 로그 (역할 6) | `AuditEventListener` 동작, ADR-0009 |

---

## 시나리오 #2 — SDK 이벤트 적재 → 부모 검증 → attribution 매칭

### 목적
이벤트 수집 도메인이 정상 동작하고, `notification_logs ↔ user_action_logs` 부모-자식 관계가 보장되는지 검증.
KPI 정의서 §1.1 "전환 이벤트 정의" 와 일치하는지 확인.

### 선행 조건
- 시나리오 #3에서 발송된 캠페인의 notification_logs row 1개 존재 (logId=98234521, projectId=1, userId=78901, sentAt=현재시각)
- SDK는 X-API-Key 헤더 사용 (S14P31B101-193 합의 결과)

### 단계

| # | 액션 | 책임 도메인 | 기대 |
|---|---|---|---|
| 1 | `POST /api/v1/events/actions` with `actionType=OPEN`, `logId=98234521`, valid X-API-Key | event ingest (역할 6, 적재) + 인증/검증 파트 (X-API-Key 헤더 처리, 역할은 S14P31B101-193 책임 경계 합의 후 확정) | 201 Created, user_action_logs row 적재 |
| 2 | 같은 logId로 `actionType=CLICK`, `actionTarget=https://...` | event ingest | 201 Created |
| 3 | 같은 logId로 `actionType=CONVERT` | event ingest | 201 Created |
| 4 | **부모 검증 실패**: `logId=999999999` (존재하지 않음) | event ingest | **400 LOG_NOT_FOUND** |
| 5 | **다른 프로젝트 logId**: project=2의 logId 사용, project=1 API Key | auth + event | **400 LOG_NOT_FOUND** (정보 노출 방지 — 401/403이 아님) |
| 6 | **무효 API Key**: 형태 불일치 (`pk_test_xxxx`) | auth (API Key validator) | **401 INVALID_API_KEY** |
| 7 | **attribution 윈도우 밖**: `actionAt = sentAt - 31일` (즉 발송 전 31일) | event ingest | 201 적재되나, 분석 집계 시 이 row는 카운트 안 됨 (KPI 정의서 §4.2) |
| 8 | DB 직접 확인: `user_action_logs` row 4건 (단계 1, 2, 3, 7 — 4번/5번/6번은 적재 안 됨) | — | 카운트 일치 |
| 9 | DB 직접 확인: `user_action_logs.project_id` 모두 1 | — | 비정규화 정합성 (123-A 머지본) |

### 기대 결과
- 단계 1~3 모두 201, 단계 4~6은 차단
- 단계 5의 400 응답이 "키 잘못" 또는 "권한 없음"이 아닌 **`LOG_NOT_FOUND`** (다른 프로젝트의 logId 존재 사실 자체를 노출하지 않음)
- 단계 8, 9의 DB 정합성 확인

### 실패 시 진단

| 증상 | 의심 도메인 | 확인 |
|---|---|---|
| 단계 4에서 500 또는 다른 에러 코드 | event ingest 부모 검증 | `UserActionIngestService#record` 의 logId 조회 분기 |
| 단계 5에서 200 또는 다른 프로젝트 데이터 노출 | event + auth 통합 | API Key validator → projectId와 logId.projectId 비교 가드 (S14P31B101-193 후속) |
| 단계 6에서 200 | auth (API Key 검증 미적용) | `ApiKeyValidator` Bean 등록 + 인증 필터 등록 여부 |
| 단계 9에서 project_id null/불일치 | event ingest 비정규화 | 123-A 머지본 `UserActionIngestService` 의 project_id 복사 로직 |

---

## 시나리오 #3 — 세그먼트 생성 → 캠페인 예약 → 발송 → 발송 결과 적재

### 목적
세그먼트 → 캠페인 → 발송 도메인이 사슬로 연결되는지 검증.
6개 도메인 중 가장 긴 통합 경계 — 끊긴 지점을 시나리오 안에서 즉시 식별 가능해야 한다.

### 선행 조건
- 시나리오 #1 통과, 멤버 X (OPERATOR)로 로그인
- Project A에 고객 5명 시드 (모두 PUSH 채널 토큰 보유)
- 메시지 템플릿 1개 시드 (이름: "데모 PUSH", 본문: "안녕하세요 {{name}}님")

### 단계

#### §1. 세그먼트 정의 + 미리보기 (역할 3 책임)
| # | 액션 | 기대 |
|---|---|---|
| 1.1 | `POST /api/v1/segments` (조건 트리: "최근 7일 내 OPEN 1회 이상") | 201, segmentId 반환 |
| 1.2 | `GET /api/v1/segments/{id}/preview` | 200, `targetCount > 0` (수 초 이내) |

#### §2. 세그먼트 평가 (역할 3 책임)
| # | 액션 | 기대 |
|---|---|---|
| 2.1 | DB 확인: `segment_members` 테이블 또는 평가 결과 | targetCount와 일치 |

#### §3. 캠페인 정의 (역할 4 책임)
| # | 액션 | 기대 |
|---|---|---|
| 3.1 | `POST /api/v1/campaigns` (segmentId, templateId, 즉시 발송) | 201, campaignId 반환 |
| 3.2 | `POST /api/v1/campaigns/{id}/execute` | 202 Accepted, executionId 반환 |

#### §4. CampaignMessage 확정 (역할 4 책임)
| # | 액션 | 기대 |
|---|---|---|
| 4.1 | DB 확인: `campaign_messages` row | 발송 직전 메시지 본문 변수 치환 완료 |

#### §5. 발송 워커 → 결과 적재 (역할 5 책임)
| # | 액션 | 기대 |
|---|---|---|
| 5.1 | 발송 워커 큐 처리 (RabbitMQ 또는 Redis 큐) | 5건 모두 처리 |
| 5.2 | DB 확인: `notification_logs` row 5건, status=`SENT` 또는 `DELIVERED` | targetCount와 일치 |
| 5.3 | Kafka `notification-send-result` 토픽 메시지 5건 발행 | S14P31B101-177 컨슈머가 적재 |
| 5.4 | DB 확인: `notification_logs` 의 project_id, campaign_id, channel 비정규화 | 모두 채워짐 |

### 기대 결과
- §1~5 모든 단계 통과
- §5.2의 카운트가 §1.2의 미리보기 카운트와 일치 (5건)
- §5.3 Kafka 메시지가 발행됨 (외부 합의 끝, S14P31B101-177)

### 실패 시 진단

| 증상 | 의심 도메인 | 확인 |
|---|---|---|
| §1.2 미리보기 카운트=0 | segment 조건 평가 | 시드 데이터의 OPEN 이벤트가 실제로 user_action_logs 에 있는지 |
| §3.2 응답에 executionId 없음 | campaign 정의 | campaign_executions 테이블 row 생성 여부 |
| §4.1 row 없음 | campaign 발송 직전 처리 | 발송 워커가 CampaignMessage 생성 단계 누락 |
| §5.2 row 없음 | delivery 워커 | RabbitMQ/Redis 큐 적체, 워커 로그 확인 |
| §5.3 Kafka 메시지 없음 | delivery + analytics 통합 | S14P31B101-177 컨슈머 / DLQ 확인 (S14P31B101-180) |
| §5.4 project_id null | delivery (역할 5) 비정규화 누락 | notification_logs 적재 시점에 project_id/campaign_id/channel 복사 |

---

## 시나리오 #4 — 사용자 반응 → 집계 → 대시보드 KPI 표시

### 목적
시나리오 #2, #3 데이터가 분석 도메인에서 정확히 집계되어, **대시보드/리포트/API 응답이 모두 같은 숫자**를 보이는지 검증 (KPI 정의서 §1 단일 정의 원칙).

### 선행 조건
- 시나리오 #3 통과 (notification_logs 5건 적재)
- 시나리오 #2 통과 (user_action_logs 4건 — OPEN 1, CLICK 1, CONVERT 1, OPEN 1[윈도우 밖])

### 단계

| # | 액션 | 책임 도메인 | 기대 |
|---|---|---|---|
| 1 | hourly 집계 트리거 (`POST /internal/stats/aggregate/hourly?bucketHour=...`) 또는 5분 wait (HourlyAggregator @Scheduled) | analytics (역할 6) | hourly 테이블 UPSERT |
| 2 | DB 확인: `hourly_*` 테이블의 sent_count | 5 (시나리오 #3 발송 5건) |
| 3 | DB 확인: `hourly_*` 테이블의 open_count | 1 (시나리오 #2 OPEN 1건, 윈도우 밖 1건은 제외) |
| 4 | daily 롤업 트리거 (`POST /internal/stats/rollup/daily?force=true`) | analytics | daily 테이블 UPSERT, hourly 합산 결과 |
| 5 | DB 확인: `daily_*` 테이블 sent_count = 5 | — | hourly 합산 일치 |
| 6 | `GET /api/v1/projects/{projectId}/dashboard` (DashboardOverviewController) | analytics + auth | 200, 6개 KPI 표시 |
| 7 | 응답의 sent_count = 5, open_rate = 1/5 = 0.2, click_rate = 1/5 = 0.2, convert_rate = 1/5 = 0.2 | — | KPI 정의서 §3 비율 공식과 일치 |
| 8 | `GET /api/projects/{projectId}/campaigns/{campaignId}/stats` (AnalyticsController) | analytics | 같은 campaignId 기준 같은 숫자 |
| 9 | `GET /api/v1/projects/{projectId}/reports/campaign-funnel?campaignId={id}` (CampaignFunnelController) | analytics | 5 발송 → 1 OPEN → 1 CLICK → 1 CONVERT 단계별 표시 |
| 10 | `GET /api/v1/audit-logs?projectId=1&date=today` (AuditController — 정확 path는 spec_v2.md 참조) | analytics audit | 시나리오 #1, #3에서 발생한 감사 이벤트 (로그인, 캠페인 활성화 등) |

> 엔드포인트 path는 컨트롤러 레이어가 아직 v1 prefix 통일 작업이 완료되지 않아 일부 혼재 (`/api/v1/...` vs `/api/...`). QA Run 시점에 [butterfly_api_spec_v2.md](../../butterfly-reporting/docs/butterfly_api_spec_v2.md) 또는 컨트롤러 코드의 `@RequestMapping` 으로 최종 확인.

### 기대 결과
- 단계 6, 7, 8, 9의 같은 카운트가 **숫자 단위까지 정확히 일치** (단일 정의 원칙)
- 단계 3에서 attribution 윈도우 밖 OPEN이 카운트되지 않음
- 단계 10에서 감사 로그가 immutable (UPDATE/DELETE 시도 시 권한 부족 — S14P31B101-182 후 검증)

### 실패 시 진단

| 증상 | 의심 도메인 | 확인 |
|---|---|---|
| 단계 2의 sent_count ≠ 5 | analytics hourly 집계 | hourly 워커 로그, UPSERT 자연 키 충돌 |
| 단계 3의 open_count = 2 (윈도우 밖 포함) | analytics attribution | KPI 정의서 §4.2 윈도우 필터 적용 여부 (집계 SQL의 `WHERE actionAt BETWEEN sentAt AND sentAt+30d`) |
| 단계 5의 daily ≠ hourly 합산 | analytics daily 롤업 | DailyRollupService (S14P31B101-191) 의 hourly 합산 SQL |
| 단계 7의 비율 공식 불일치 | analytics API + KPI 정의서 | KPI 정의서 §3 vs AnalyticsServiceImpl |
| 단계 8과 단계 6의 sent_count 다름 | analytics API 다중 출처 | 같은 집계 테이블을 보는지 (집계 테이블 우선 정책 ADR-0007) |
| 단계 10에서 audit 비어있음 | audit (역할 6) | AuditEventListener 동작, ADR-0009 |

---

## 부록 A — 시나리오 실행 자동화

5/12, 5/13, 5/14 QA Run 시 다음 절차로 실행:

1. dev 환경 DB 초기화 (`./scripts/qa/reset-dev-db.sh`) — 시드 스크립트는 별도 PR로 추가 예정
2. 시나리오 #1 → #2 → #3 → #4 순서로 실행 (의존 관계 있음)
3. 각 시나리오 끝나면 결과를 [QA Run 보고서 양식](./qa-run-template.md)에 기록 (P0 결함은 즉시 Jira 이슈)
4. 마지막에 KPI 정의서와 화면 표시값 1:1 비교 (단계 7~9)

**자동화 우선순위**: 시나리오 #1, #2는 SpringBootTest 기반 통합 테스트로 자동화 가능 (Code Freeze 후 진행). #3, #4는 발송 워커 + 집계 배치 시간 의존이라 수동 + DB 직접 확인 권장.

---

## 부록 B — 외부 의존 Stub 목록

데모 안정성을 위해 다음을 Stub 어댑터로 대체:

| 외부 의존 | Stub 동작 | 위치 |
|---|---|---|
| FCM (앱 푸시) | 즉시 SENT 응답 | (역할 5 채널 어댑터) |
| APNS | 즉시 SENT 응답 | (역할 5) |
| 채널 게이트웨이 retry | Stub은 첫 시도 성공으로 처리 | (역할 5) |
| 외부 회차 데이터 (campaign_executions) | 정합성 검증 시 stub | S14P31B101-187 어댑터 (이미 머지) |

실제 외부 채널 연동은 Code Freeze 후 별도 데모 환경에서 검증.
