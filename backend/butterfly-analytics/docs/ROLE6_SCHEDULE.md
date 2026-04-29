# 역할 6 (이재영) — 분석/대시보드/운영/통합 검증 개발 일정 (Todo)

## Context

- **역할 6 책임**: 분석 / 대시보드 / 통합 검증 + 운영 정책·관측성·배포 (기획서 §8-2, requirements §12-3)
- **작성일**: 2026-04-29 (수)
- **최종 데드라인**: **2026-05-22 (목)** — 발표
- **백엔드 Code Freeze**: **2026-05-15 (금) 18:00**
- **범위**: 백엔드 + 통합 검증까지. 프론트 화면은 별도
- **Jira 미완료 32건**: 에픽 4 / 스토리 6 / 작업 22 (진행 중 2)

---

## Week 1 (4/29 수 ~ 5/3 일) — 기반 잠금 주

### T1 아키텍처/스키마 [에픽 S14P31B101-42]
- [ ] **4/29~30** S14P31B101-46 모듈 간 직접 의존 차단 규칙 수립 *(진행 중)*
- [x] **4/29 완료** S14P31B101-47 DB/ERD 저장 전략 + 반정규화 기준 확정 → [ADR-0010](../../docs/adr/0010-storage-strategy-and-denormalization.md)
- [ ] **5/1~5/2** S14P31B101-121 [analytics] DDL Flyway 마이그레이션 적용 ⭐ **게이트**
- [ ] **5/1~5/2** S14P31B101-125 [analytics] 감사 로그 트리거 + DB 권한 분리 적용

### T3 품질/관측성/배포 [에픽 S14P31B101-44]
- [ ] **4/29~30** S14P31B101-64 Docker Compose 로컬 표준 환경
- [ ] **4/29~30** S14P31B101-63 API/워커 분리 배포 + 롤백 절차
- [ ] **4/30~5/1** S14P31B101-62 환경별 설정 분리 + 시크릿 관리 (지연 회수)

### T4 분석/대시보드 [에픽 S14P31B101-16]
- [ ] **5/2~5/3** S14P31B101-76 KPI 지표 정의서 + Attribution 기준 확정 (5/5 due → 5/3 앞당김)

### Week 1 마일스톤
- ✅ **5/2 (토)** Flyway DDL이 develop 머지됨 → 다음 주 컨슈머 작업 가능
- ✅ **5/2 (토)** 감사 트리거 + 권한 분리 적용
- ✅ **5/3 (일)** KPI/Attribution 문서 확정

---

## Week 2 (5/4 월 ~ 5/10 일) — 데이터 파이프라인 주

### T1 적재/집계
- [ ] **5/4~5/6** S14P31B101-122 [analytics] 발송 결과 Kafka 컨슈머 → notification_logs 적재
- [ ] **5/4~5/6** S14P31B101-123 [analytics] 사용자 반응 이벤트 수신 API (POST /api/v1/events/actions)
- [ ] **5/7~5/9** S14P31B101-124 [analytics] hourly 스트림 + daily 롤업 UPSERT

### T3 CI/QA
- [ ] **5/4~5/6** S14P31B101-65 CI/CD 파이프라인 초안 구성
- [ ] **5/6~5/8** S14P31B101-67 MVP QA 기준 + 통합 테스트 시나리오 확정
- [ ] **5/7~5/8** S14P31B101-68 부하 테스트 + 장애 대응 검증 시나리오

### T4 사용자 가치
- [ ] **5/8~5/9** S14P31B101-40 전환 이벤트 정의 + 성과 집계 기준 구현 (T1-124 위에서)

### Week 2 마일스톤
- ✅ **5/6 (수)** Kafka 컨슈머 → notification_logs 적재 동작
- ✅ **5/6 (수)** 사용자 반응 이벤트 수신 API 가동
- ✅ **5/9 (토)** hourly/daily 집계 1회 통과
- ✅ **5/10 (일)** CI 게이트 그린

> 사전 작업: 4/30까지 역할 5와 발송 결과 Kafka 토픽 스키마 합의 미팅

---

## Week 3 (5/11 월 ~ 5/15 금) — 대시보드/리포트 + Code Freeze

### T4 대시보드/리포트 API
- [ ] **5/11~5/12** S14P31B101-33 운영자 대시보드 — 프로젝트 운영 상태 조회
- [ ] **5/12~5/13** S14P31B101-34 분석가 — 캠페인 성과 (전환율/매출)
- [ ] **5/12~5/13** S14P31B101-90 플로우 단계별 이탈률 (펀널) 리포트
- [ ] **5/12~5/13** S14P31B101-92 세그먼트/채널별 성과 비교 리포트
- [ ] **5/14** S14P31B101-35 감사 로그 조회 + CSV 내보내기
- [ ] **5/14** S14P31B101-91 리포트 CSV 다운로드

### T1 정합성
- [ ] **5/13~5/14** S14P31B101-126 [analytics] 발송 정합성 검증 배치 + 관리자 트리거 API

### T3 운영
- [ ] **5/14~5/15** S14P31B101-66 백업/복구 운영정책 + 리허설 (5/2 due → 인프라 안정화 후로 보류)

### T5 옵션 (여유 시) [에픽 S14P31B101-130]
- [ ] **여유 시** S14P31B101-141 analytics 모듈 @EntityScan 세분화
- [ ] **여유 시** S14P31B101-142 AnalyticsJpaAutoConfig 신설

### Week 3 마일스톤
- ✅ **5/12 (화)** 대시보드 API 통과
- ✅ **5/13 (수)** 캠페인 성과 + 펀널 + 비교 리포트 통과
- ✅ **5/14 (목)** 감사 로그/리포트 CSV + 정합성 검증 통과
- ⛔ **5/15 (금) 18:00 백엔드 Code Freeze**

> 사전: 5/10 주말에 컨트롤러/DTO 스켈레톤만 PR로 올려두고 본 작업 시작

---

## Week 4 (5/18 월 ~ 5/22 목) — 통합 검증 + 발표 준비

> 역할 6이 통합 점검을 주도 (requirements §12-3)

- [ ] **5/18 (월)** End-to-End 통합 시나리오 1차 통과 (이벤트 → 고객 → 세그먼트 → 캠페인/플로우 → 발송 → 분석)
- [ ] **5/18 (월)** 회귀 이슈 트리아지
- [ ] **5/19 (화)** 멀티테넌트 권한 분리 회귀
- [ ] **5/19 (화)** request_id 추적 시연 (이슈 -49 산출물 활용), DLQ/재시도 검증
- [ ] **5/19~5/20** 부하 테스트 1회 시연 시나리오 (T3-68 산출물)
- [ ] **5/20 (수)** 시연용 데이터셋 (시드 + 캠페인 + 발송 결과) 고정
- [ ] **5/21 (목)** 발표 슬라이드/아키텍처 다이어그램 동결
- [ ] **5/21 (목)** Demo dry-run 2회
- [ ] **5/22 (목)** 최종 발표

### 발표 준비물 (requirements §13-3)
- [ ] 시스템 아키텍처 다이어그램
- [ ] E2E 흐름 데모 시나리오
- [ ] 멀티테넌트 / 비동기 / 채널 추상화 / 성과 측정 4축 설명

---

## 위험 및 완충 (Risk Buffer)

- [ ] **W1 Flyway 지연 위험** — 4/30까지 DDL 초안 PR, 5/1 머지 가능 상태 확보
- [ ] **W2 Kafka 토픽 스키마** — 4/30 역할 5와 합의 미팅
- [ ] **W3 대시보드 API 5/12~13 폭주** — 5/10 주말에 컨트롤러/DTO 스켈레톤 PR 선제 등록
- [ ] **T3-66 due 갱신** — Jira에서 5/2 → 5/15로 갱신
- [ ] **T5 (P2 analytics)** — 발표 후로 미루는 것 디폴트, Week 3 여유 시에만 처리

---

## Definition of Done

### 5/15 Code Freeze 시점
- [ ] `./gradlew clean build` 전체 통과 (ArchUnit baseline 포함)
- [ ] Flyway 마이그레이션이 빈 DB 위에 깨끗이 적용
- [ ] 발송 결과 Kafka → notification_logs 적재가 통합 환경에서 동작
- [ ] POST /api/v1/events/actions 멱등성 보장 적재
- [ ] hourly/daily 집계 배치 1회 이상 실행 + 대시보드 API에 비어있지 않은 응답
- [ ] 6개 API 200 OK + 의미 있는 데이터: 운영 대시보드(33), 캠페인 성과(34), 펀널(90), 비교 리포트(92), 감사 로그(35), 리포트 CSV(91)
- [ ] 감사 트리거가 INSERT 외 수정/삭제 차단 (T1-125)
- [ ] CI 단위/슬라이스/통합 테스트 전부 그린

### 5/22 발표 시점 추가
- [ ] E2E 데모 시나리오 라이브 1회 통과
- [ ] 멀티테넌트 권한 회귀 시나리오 통과

---

## Jira due-date 동기화 액션 (별도)

보고 정합성 위해 갱신 권장:
- [ ] T3-62 (4/28 → 5/1)
- [ ] T3-63 (4/29 → 4/30)
- [ ] T3-64 (4/29 → 4/30)
- [ ] T3-65 (5/1 → 5/6)
- [ ] T3-66 (5/2 → 5/15)
- [ ] T1-47 (5/5 → 5/2)
- [ ] T4-33 (5/9 → 5/12)
- [ ] T4-76 (5/5 → 5/3)

---

## 핵심 파일 (참고)

- 분석 모듈: [butterfly-analytics/src/main/java/com/b101/butterfly/analytics/](../src/main/java/com/b101/butterfly/analytics/)
  - 컨트롤러: `controller/AnalyticsController.java`
  - DTO 7종: `dto/*Response.java`, `dto/*Item.java`, `dto/AnalyticsQuery.java`
  - 서비스: `service/AnalyticsService.java`, `service/AnalyticsServiceStub.java` (← 실제 구현으로 교체)
  - 에러: `exception/AnalyticsErrorCode.java`
- 스키마/ADR:
  - [SCHEMA_DESIGN.md](SCHEMA_DESIGN.md)
  - [butterfly_api_spec_v2.md](butterfly_api_spec_v2.md)
  - [adr/0007-analytics-aggregation-tables-v2.md](../../docs/adr/0007-analytics-aggregation-tables-v2.md)
  - [adr/0008-audit-log-architecture-v2.md](../../docs/adr/0008-audit-log-architecture-v2.md)
  - [adr/0009-audit-log-relocation-to-reporting.md](../../docs/adr/0009-audit-log-relocation-to-reporting.md)
  - [adr/0010-storage-strategy-and-denormalization.md](../../docs/adr/0010-storage-strategy-and-denormalization.md)
- 운영 로그 정책: [docs/operations/log-policy.md](../../docs/operations/log-policy.md)
- 재사용 인프라: `ApiResponse<T>`, `BusinessException`, `RequestIdFilter`, MDC, ArchUnit baseline
