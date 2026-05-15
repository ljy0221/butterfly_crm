# [Security Request] 감사 로그 detail 마스킹 정책 강제(enforcement)

> Owner: 이재영 (분석/감사 — 역할 6)
> Requester → Reviewer: **보안 담당** (역할 4 — 인증/인가)
> 최초 작성: 2026-05-15
> 상태: **draft (요청)** — 보안 담당 검토 대기
> 관련 ADR: [ADR-0008](../adr/0008-audit-log-architecture-v2.md) §"비밀값 마스킹 규칙(강화)"
> 관련 코드: [`AuditRecordedEvent`](../../butterfly-common/src/main/java/com/b101/butterfly/global/event/AuditRecordedEvent.java), [`AuditEventListener`](../../butterfly-reporting/src/main/java/com/b101/butterfly/reporting/audit/listener/AuditEventListener.java)
> 관련 프론트 변경: 본 PR `feat/S14P31B101-audit-log-names` — detail JSON expander(기본 접힘) + 민감정보 경고 배너 도입

## 1. 한 줄 요약

`AuditRecordedEvent.detail` (Map<String, Object>) 에 들어가는 값에 **비밀값 평문이 섞이지 않도록 시스템 레벨에서 강제할 메커니즘**이 필요합니다. 현재는 ADR-0008 §"비밀값 마스킹 규칙"이 문서로만 존재하고 강제(enforcement)가 없어, 호출자 누락 시 평문이 그대로 저장·노출됩니다.

## 2. 배경 / 현재 상태

### 2.1 ADR-0008 의 마스킹 규칙 (문서 정착)

```
- API Key 원문, FCM service account JSON, 비밀번호, 세션 토큰 → 저장 금지
- 변경 추적이 필요한 비밀값은 SHA-256 해시 앞 8자만 기록
- 마스킹 책임은 호출자(도메인 서비스) — AuditLogService는 마스킹 유틸 함께 제공
```

ADR-0008 §"위험과 보완" 에 이미 명시되어 있는 자체 진단:

> 비밀값 해시화 책임이 호출자에 있어 누락 위험 — **코드 리뷰 체크리스트 필수**

### 2.2 코드 현황

| 위치 | 마스킹 강제 메커니즘 | 평가 |
|---|---|---|
| 도메인 서비스(발행자) | 호출자 코드에서 직접 `MaskingUtil` 호출(있는 경우) | ❌ 누락 시 컴파일·런타임 통과 |
| `AuditRecordedEvent` canonical constructor | eventId/occurredAt 자동 채움만 수행 | ❌ detail 검사 없음 |
| `AuditEventListener` | detail 을 그대로 `auditLogRepository.save()` | ❌ 검사 없음 |
| `AuditLog` 엔티티 | `@Immutable` + setter 미제공 | ❌ 값 검사 없음 |
| DB 트리거 / GRANT (3중 방어선) | UPDATE/DELETE 차단 | ❌ INSERT 시점의 평문은 막지 못함 |

**즉 모든 방어선은 "기록된 이후"를 보호하고, "기록 시점에 무엇이 들어가는지"는 호출자의 양심에 위임**되어 있습니다.

### 2.3 프런트엔드 보강(이번 PR)

본 PR(`feat/S14P31B101-audit-log-names`)에서 임시 완충 장치로 다음을 추가했습니다.

- 상세 모달의 `detail` JSON 섹션을 **기본 접힘** expander 로 변경 — 어깨너머 노출 차단
- 펼친 상태에서 "민감 정보가 포함될 수 있습니다" 경고 배너 노출

다만 이는 **노출 통제(display-side)** 일 뿐, **저장 통제(write-side)** 는 아닙니다. CSV export([`AuditLogCsvExportService`](../../butterfly-reporting/src/main/java/com/b101/butterfly/reporting/audit/service/AuditLogCsvExportService.java))로 반출되면 expander 효과가 무의미합니다.

### 2.4 실제 위험 시나리오

1. **신규 도메인 개발자**가 비밀번호 변경 audit 발행 시 `detail: {"newPassword": rawValue}` 로 호출 → 평문 영구 저장 + OWNER 화면 + CSV 까지 유출.
2. **결제 모듈** 도입 시 카드번호 마지막 4자리 외 값이 들어갈 위험.
3. **API Key** 변경 audit — ADR-0008 의 명시된 금지 항목인데, key prefix(`pk_live_`) 만 저장해야 하지만 호출자 실수로 전체 32자가 들어갈 수 있음.

## 3. 요청 사항

### 3.1 단기 (이번 스프린트 권고)

| # | 항목 | 책임 | 비용 |
|---|---|---|---|
| 1 | `MaskingUtil` 단일 진실원 정착 + 키 화이트/블랙리스트 정의 (예: `password`, `passwordHash`, `apiKey`, `secret`, `token`, `credential`, `cardNumber`, `cvv` 등) | 보안 담당 | S |
| 2 | `AuditRecordedEvent` canonical constructor 에서 detail key 검사 — 블랙리스트 키 존재 시 build 실패 또는 런타임 fail-fast (운영 결정) | 보안 담당 + 본인(역할 6) | M |
| 3 | ArchUnit 규칙: `AuditRecordedEvent.of(...)` 직접 호출 금지, `MaskingAuditEventPublisher` 같은 단일 경유점만 허용 | 보안 담당 | S |

### 3.2 중기 (Post-MVP)

| # | 항목 | 책임 |
|---|---|---|
| 4 | CSV export 시 `detail` 컬럼은 기본 제외, 옵션으로만 포함(`--include-detail`) + 별도 감사 로그 기록 | 본인(역할 6) |
| 5 | OWNER 화면에서는 detail 숨김, ADMIN 만 노출 (권한 분리) — 또는 detail 일부 필드만 화이트리스트로 노출 | 본인 + 보안 담당 |
| 6 | 운영 환경 정기 점검 — `audit_logs.detail` 전수 스캔하여 블랙리스트 키 평문 잔존 여부 확인 | 보안 담당 |

### 3.3 보안 담당 의사 결정 요청

- **(A) 마스킹 미준수 시 동작**: `fail-fast` (BusinessException 발행 → 트랜잭션 롤백) vs `warn-and-store` (마스킹된 값으로 자동 치환 + WARN 로그)
- **(B) 블랙리스트 키 목록 확정**: 위 §3.1 #1 후보 외 추가 키 검토
- **(C) 기존 audit_logs 행에 대한 처리**: 운영 적용 전 잔존 평문 점검·정정 절차 필요 여부

## 4. 본인(역할 6) 측에서 제공 가능한 것

- `AuditRecordedEvent` / `AuditEventListener` 변경 PR — 보안 담당이 정한 규칙 반영
- 기존 audit_logs 행 스캔 스크립트 (블랙리스트 키 존재 여부 통계)
- ArchUnit 룰 작성 — 보안 담당이 룰 의도 확정 후
- 프런트 화면 변경 (OWNER/ADMIN 분리 등) — 결정 후

## 5. 일정 제안

| 단계 | 목표 일자 | 책임 |
|---|---|---|
| 보안 담당 검토 회신 | 2026-05-19 | 보안 담당 |
| §3.1 #1~3 합의 + 구현 PR | 2026-05-22 (5/22 시연 이전) | 양측 |
| §3.2 #4~6 합의 | Post-MVP (5/22 이후) | 양측 |

## 6. 회신 요청 포맷

다음 항목에 대해 코멘트/이슈/Confluence 어느 채널로든 회신 부탁드립니다.

```
1. §3.3 (A) 동작 정책: [ ] fail-fast  [ ] warn-and-store  [ ] 기타: ___
2. §3.3 (B) 블랙리스트 키 최종 목록:
3. §3.3 (C) 기존 행 점검 절차 필요 여부: [ ] 필요  [ ] 불필요  / 사유:
4. §3.1 우선순위 1~3 중 본 스프린트 가능한 항목:
5. 추가 코멘트:
```

---

## 부록 A — 본인이 즉시 차단한 노출 경로 (이번 PR)

- 상세 모달 detail JSON 기본 접힘 (`AuditLogDetailModal.tsx`)
- 펼침 시 경고 배너
- 멤버명/이메일은 행위자(actor) 만 노출, 대상(target) 의 이름은 의도적으로 비노출 — `targetType` + `#ID` 만

## 부록 B — 참고 자료

- [ADR-0008 §"비밀값 마스킹 규칙(강화)"](../adr/0008-audit-log-architecture-v2.md)
- [Proposal 04: audit_logs DB 권한 분리](./04-audit-logs-db-permission-separation.md)
- [`AuditRecordedEvent`](../../butterfly-common/src/main/java/com/b101/butterfly/global/event/AuditRecordedEvent.java)
- [`AuditLogCsvExportService`](../../butterfly-reporting/src/main/java/com/b101/butterfly/reporting/audit/service/AuditLogCsvExportService.java)
