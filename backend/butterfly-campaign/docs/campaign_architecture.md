# 🦋 Butterfly CRM - Campaign & Template Domain Context

이 문서는 Butterfly CRM 플랫폼의 핵심 기능인 **푸시 알림/메시지 발송 캠페인 및 템플릿 관리 시스템**에 대한 백엔드 비즈니스 로직, 아키텍처 결정 사항, 그리고 데이터베이스 스키마를 정의합니다.
AI 에이전트(Claude)는 코드를 작성하거나 리뷰할 때 **반드시 이 문서의 규칙과 아키텍처를 준수**해야 합니다.

## 1. 도메인 개요 및 권한
* **목적:** 사용자가 고객에게 보낼 메시지 템플릿을 만들고, 세그먼트(타겟)를 설정하여, 즉시/예약/반복 발송(캠페인)을 스케줄링하는 기능.
* **권한 (Role):** `EDITOR` 권한 이상의 사용자(Owner, Admin, Editor)만 캠페인 및 템플릿의 생성/수정/삭제/발송 제어가 가능합니다. (`VIEWER`는 조회 불가능 또는 기능 제한)

---

## 2. 핵심 비즈니스 로직 및 아키텍처 결정 사항 (CRITICAL)

### 2.1. 캠페인 설정(Campaign)과 실행 이력(Execution)의 분리
* **설계 의도:** 반복 발송 시 하나의 레코드에서 상태 값을 계속 덮어쓰면 감사(Audit)와 통계 이력이 소실됩니다. 이를 방지하기 위해 **설정 마스터 테이블(`campaigns`)**과 **실제 발송 회차 테이블(`campaign_executions`)**을 완전히 분리했습니다.
* `campaigns`: 캠페인의 생애 주기(초안, 진행 중, 종료)와 반복 스케줄 정보만 담습니다.
* `campaign_executions`: 스케줄러가 발송을 트리거할 때마다 `run_round`(회차) 단위로 레코드가 생성되며, 실제 발송 성공/실패 여부를 담습니다.

### 2.2. 상태(Status) 관리 파이프라인
**[캠페인 마스터 (`campaign_status`)]**
* `DRAFT`: 생성 완료 상태. (아직 발송 대기 아님)
* `ACTIVE`: 사용자가 '활성화'를 누른 상태. 스케줄러가 이 상태인 캠페인의 `next_run_at`을 폴링하여 발송을 트리거합니다.
* `PAUSED`: 활성화된 반복 캠페인을 임시 중단한 상태 (스케줄러가 무시함).
* `ENDED`: 단발성 발송 완료, 또는 반복 횟수(`recurring_count`) 도달 시 변경.

**[실행 회차 (`campaign_execution_status`)]**
* `READY`: 스케줄러에 의해 발송 회차가 생성된 직후.
* `SEND`: Kafka 큐에 적재되어 발송 워커가 처리 중인 상태.
* `SUCCESS` / `FAIL`: 실제 발송 처리 결과.

### 2.3. 발송 트리거 방식 (Scheduler & Next Run)
* **즉시 발송:** `campaigns` 생성 후 즉시 상태를 `ACTIVE`(또는 단건이므로 곧바로 `ENDED`)로 변경하고, 스케줄러를 거치지 않고 바로 트랜잭션 내에서 `campaign_executions`를 생성한 뒤 Kafka로 발행합니다.
* **예약/반복 발송:** DB 폴링 방식을 사용합니다.
    * Spring Boot 스케줄러가 주기적으로 `SELECT * FROM campaigns WHERE campaign_status = 'ACTIVE' AND next_run_at <= NOW()` 쿼리를 실행합니다.
    * 트리거된 후, 다음 발송 주기를 `recurring_cron`을 통해 파싱하여 `next_run_at`을 업데이트합니다. 목표 횟수에 도달하면 `ENDED`로 처리합니다.

### 2.4. 스케줄링 데이터의 이중 관리 (하이브리드 방식)
* **이유:** Cron 표현식은 스케줄러가 이해하기 좋지만, UI 상태로 역변환(Parsing)하기 매우 어렵습니다.
* **규칙:** 스케줄러 동작을 위한 `recurring_cron` 컬럼과, 프론트엔드 UI 렌더링/필터링을 위한 정규화된 컬럼(`repeat_type`, `repeat_day_of_week`, `repeat_time` 등)을 **모두 저장**합니다. Cron 변환 로직은 Spring Boot(백엔드)가 담당합니다.

### 2.5. 개인화 변수 (`{{name}}` 등) 처리 위임
* **설계 의도:** 조인 병목 및 Kafka 페이로드 폭발을 방지합니다.
* **캠페인/템플릿 서버의 역할:** 사용자가 입력한 본문에서 정규식으로 `{{변수}}`를 추출하여 `message_templates` 테이블의 `required_variables` JSON 컬럼에 저장만 합니다. (예: `["name", "region"]`) **절대 여기서 치환 연산을 하지 않습니다.**
* **치환의 주체:** Spring Batch가 대상자 목록을 구성할 때 필요시 동적 데이터를 스냅샷 뜨고, 최종 문자열 치환은 Kafka 메시지를 소비하는 **발송 워커(Delivery Worker)**가 발송 0.01초 전에 수행합니다 (Data Freshness 보장).

### 2.6. 외래키(FK) 설계 최적화
* `campaigns` 테이블은 `channel` 정보를 직접 가지지 않습니다. 캠페인이 참조하는 `message_templates`에 정의된 `channel` ENUM을 따라갑니다. (데이터 정규화 및 모순 방지)

---

## 3. 데이터베이스 스키마 핵심 요약

### 3.1. `message_templates` (메시지 템플릿)
* `message_template_id` (PK)
* `project_id` (FK) / `member_id` (FK - 생성자)
* `channel`: ENUM (`PUSH`, `EMAIL`, `SMS`, `WEBHOOK`, `KAKAO`) - **채널 정보는 템플릿에서 관리함**
* `message_templates_body`: `{{name}}` 등의 변수가 포함된 본문
* `required_variables`: 서버 단에서 본문을 파싱해 추출한 변수 배열 (JSON 타입, 예: `["name"]`)

### 3.2. `campaigns` (캠페인 마스터)
* `campaign_id` (PK)
* `message_template_id` (FK) - 채널 정보 획득 용도
* `segment_id` (FK, Nullable) - 타겟 조건. Null이면 전체 발송
* `campaign_status`: ENUM (`DRAFT`, `ACTIVE`, `PAUSED`, `ENDED`)
* `recurring_cron`: 실제 스케줄러가 사용하는 Cron 식
* `repeat_type`, `repeat_day_of_week` 등: UI 및 통계 쿼리용 정규화 데이터
* `next_run_at`: **(중요)** 스케줄러가 폴링하는 기준 시간 (인덱스 적용됨)
* `recurring_execution_count`: 현재까지 트리거된 횟수 (목표치인 `recurring_count`와 비교)

### 3.3. `campaign_executions` (발송 실행 이력)
* `campaign_execution_id` (PK)
* `campaign_id` (FK)
* `run_round`: 발송 회차 (1, 2, 3...)
* `campaign_execution_status`: ENUM (`READY`, `SEND`, `SUCCESS`, `FAIL`)
* `target_count`, `sent_count`, `failed_count`: 정합성 검증 및 통계용 컬럼

---

## 4. API 명세서

### 4.1. 템플릿 관리 API (Base: `/api/v1/templates`)
| Method | Endpoint | 설명 | 핵심 로직 및 주의사항 |
| :--- | :--- | :--- | :--- |
| **POST** | `/` | 생성 | Request Body의 내용 중 템플릿 바디를 파싱하여 `required_variables`를 자동 추출 및 저장. |
| **GET** | `/` | 목록 조회 | `channel` 및 검색어(`name`) 기반 필터링 제공. 페이지네이션. |
| **GET** | `/{id}` | 상세 조회 | 기본 정보 및 `required_variables`, 작성자 이름 반환. |
| **PUT** | `/{id}` | 수정 | 템플릿 수정 시 본문을 다시 파싱하여 `required_variables` 갱신. (연결된 `ACTIVE` 캠페인이 없을 때만 허용 권장) |
| **DELETE**| `/{id}` | 삭제 | Soft Delete (`is_deleted = true`) 적용. |

### 4.2. 캠페인 관리 API (Base: `/api/v1/campaigns`)
| Method | Endpoint | 설명 | 핵심 로직 및 주의사항 |
| :--- | :--- | :--- | :--- |
| **POST** | `/` | 생성 (초안) | 생성 직후 상태는 무조건 `DRAFT`. 이 단계에선 예약 시간이 돼도 스케줄러가 동작하지 않음. |
| **GET** | `/` | 목록 조회 | 대시보드(최근 5개) 및 전체 목록용. 상태, 타입 필터 지원. |
| **GET** | `/{id}` | 상세 조회 | 대상 세그먼트 및 템플릿 상세, `next_run_at`, 실행 횟수 등 조인/조합하여 반환. |
| **PUT** | `/{id}` | 전체 수정 | 상태가 `DRAFT` 또는 `PAUSED` 일 때만 전체 수정 가능하도록 방어 로직 필수. |
| **DELETE**| `/{id}` | 삭제 | Soft Delete. `ACTIVE` 상태면 삭제 차단 또는 경고 예외 발생. |

### 4.3. 캠페인 상태 제어 액션 (비즈니스 액션)
| Method | Endpoint | 설명 | 핵심 로직 및 주의사항 |
| :--- | :--- | :--- | :--- |
| **POST** | `/{id}/activate` | 활성화 | `DRAFT` -> `ACTIVE` 변경. 예약/반복 발송인 경우 스케줄 정보를 기반으로 `next_run_at`을 계산하여 세팅. 즉시 발송이면 이력 생성 후 즉시 큐 적재. |
| **POST** | `/{id}/pause` | 일시정지 | `ACTIVE` -> `PAUSED`. 스케줄러가 이 캠페인을 건너뛰게 함. |
| **POST** | `/{id}/resume` | 재개 | `PAUSED` -> `ACTIVE`. `next_run_at`을 현재 시점 기준으로 재계산하여 세팅. |

---

## 5. 🤖 Claude Code를 위한 가이드라인 (System Instructions)

당신(Claude)이 이 도메인의 코드를 작성하거나 리뷰할 때 **반드시 다음 사항을 검증**하십시오.

1.  **관심사 분리 확인:**
    * 스케줄러(Scheduler) 코드 안에 무거운 JOIN이나 타겟 유저를 추출하는 로직을 작성하지 마십시오. 스케줄러는 단지 `campaigns`의 상태를 변경하고 배치를 호출하거나, 단건에 대한 이벤트를 Kafka로 넘기는 역할(Trigggering)만 해야 합니다.
    * 캠페인 도메인에서 `{{name}}` 등의 문자열 `replace()` 작업을 수행하는 코드가 있다면 제거하십시오. 이는 `butterfly-delivery` (발송 워커)의 역할입니다.
2.  **데이터베이스 상태 및 동시성 방어:**
    * 단일 테이블에 모든 상태를 덮어쓰는 코드를 작성하지 마십시오. 반복 발송 로직 작성 시 반드시 `campaign_executions` 테이블에 `INSERT` 하는 로직을 포함하십시오.
    * DB 폴링 스케줄러 작성 시, 여러 서버가 동시에 폴링하여 중복 발송되는 것을 막기 위해 `ShedLock` 혹은 이에 준하는 분산 락(Distributed Lock) 어노테이션이나 로직이 포함되었는지 확인하십시오.
3.  **정규화와 편의성의 밸런스:**
    * 캠페인 엔티티를 작성할 때, `channel_id`를 템플릿과 캠페인 양쪽에 두지 마십시오. 캠페인은 템플릿 ID만 가집니다.
    * 반복 주기 저장 코드를 작성할 때, 프론트엔드의 DTO를 `recurring_cron` 문자열로 생성하는 유틸리티 메서드를 구성하십시오. (역변환 파싱 로직은 작성하지 마십시오.)
4.  **권한 검증 로직 추가:**
    * Service 레이어 메서드 상단이나 Security 레벨에서 현재 요청을 보낸 `member_id`가 속한 `project_id`와 수정하려는 자원의 `project_id`가 일치하는지, 권한이 `EDITOR` 이상인지 확인하는 검증 로직을 누락하지 마십시오.