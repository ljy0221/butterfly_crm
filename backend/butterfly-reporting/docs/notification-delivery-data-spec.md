# butterfly-notification 발송 결과 저장 명세

> notification 모듈이 플랫폼별 발송 결과(성공/실패/에러)를 **어떻게 DB에 저장하는지** 설명하는 문서입니다.

---

## 1. 전체 흐름

```
DeliveryCommand (Kafka or HTTP)
    │
    ▼
DeliveryServiceImpl.deliver()
    │
    ├─ Phase 1: 채널 어댑터가 외부 API 호출 (트랜잭션 밖)
    │       ChannelAdapter.sendBatch()
    │           → 대상마다 TargetOutcome(target, DeliveryResult) 리턴
    │
    └─ Phase 2: 결과 DB 저장 (@Transactional)
            DeliveryResultPersister.persist()
                ① notification_logs INSERT (건당 1 row)
                ② campaign_stats 카운터 증가
```

**핵심**: 외부 API 호출(Phase 1)과 DB 저장(Phase 2)이 분리되어 있습니다. 외부 호출 중 DB 커넥션을 점유하지 않습니다.

---

## 2. 저장되는 테이블

### 2.1 `SenderNotificationLog` → 테이블 `notification_logs` (발송 1건 = 1 row)

| 컬럼명 | 타입 | NULL | 설명 |
|---|---|---|---|
| `log_id` | BIGINT PK AUTO_INCREMENT | NO | 로그 고유 ID |
| `project_id` | BIGINT | NO | 프로젝트 ID |
| `campaign_id` | BIGINT | NO | 캠페인 ID |
| `user_id` | BIGINT | YES | 발송 대상 유저 ID |
| `device_id` | BIGINT | YES | 기기 ID (PUSH 채널에서만 사용) |
| `channel` | ENUM(`PUSH`,`EMAIL`,`SMS`,`WEBHOOK`,`KAKAO`) | NO | 발송 채널 |
| `recipient` | VARCHAR(500) | YES | **채널별 수신자 식별값** (아래 3절 참조) |
| `status` | ENUM(`SENT`,`DELIVERED`,`CLICKED`,`FAILED`) | NO | 발송 결과 상태 |
| `error_code` | VARCHAR(50) | YES | 에러 코드 (성공 시 NULL) |
| `error_message` | VARCHAR(500) | YES | 에러 상세 (성공 시 NULL) |
| `sent_at` | DATETIME | YES | 발송 시각 |
| `delivered_at` | DATETIME | YES | 도달 확인 시각 (콜백으로 갱신) |
| `clicked_at` | DATETIME | YES | 클릭 시각 (콜백으로 갱신) |
| `created_at` | DATETIME | NO | 레코드 생성 시각 |
| `updated_at` | DATETIME | NO | 레코드 수정 시각 |

**INSERT 시점의 값**:
- 성공: `status=SENT`, `error_code=NULL`, `error_message=NULL`, `sent_at=now`
- 실패: `status=FAILED`, `error_code=플랫폼별 코드`, `error_message=에러 상세`, `sent_at=now`

### 2.2 `campaign_stats` (캠페인별 실시간 카운터)

| 컬럼명 | 타입 | 설명 |
|---|---|---|
| `campaign_id` | BIGINT PK | 캠페인 ID |
| `total_target` | INT | 전체 대상 수 |
| `sent_count` | INT | SENT 누적 |
| `delivered_count` | INT | DELIVERED 누적 |
| `clicked_count` | INT | CLICKED 누적 |
| `failed_count` | INT | FAILED 누적 |

발송 결과 INSERT와 **같은 트랜잭션**에서 카운터가 1씩 증가합니다.

---

## 3. `recipient` 컬럼 — 채널별 저장 값

| 채널 | `recipient`에 들어가는 값 | 예시 |
|---|---|---|
| `PUSH` | FCM 토큰 | `dK1xQ3r...Zp3q` |
| `EMAIL` | 이메일 주소 | `user@example.com` |
| `SMS` | 전화번호 | `01012345678` |
| `WEBHOOK` | **항상 NULL** | (URL은 credential에 저장) |
| `KAKAO` | **항상 NULL** | (미구현) |

---

## 4. 플랫폼별 `error_code` / `error_message` 상세

### 4.1 FCM (PUSH)

> 소스: `FcmService.java` — Firebase Admin SDK의 `MessagingErrorCode`를 그대로 문자열로 저장

| 상황 | `error_code` 값 | `error_message` 값 | 재시도 가능 |
|---|---|---|---|
| 발송 성공 | NULL | NULL | - |
| 토큰이 비어있음 | `DELIVERY_015` | `Push token이 누락되었습니다.` | X |
| 토큰 만료/삭제 | `UNREGISTERED` | Firebase SDK 에러 메시지 원문 | X |
| 잘못된 요청 파라미터 | `INVALID_ARGUMENT` | Firebase SDK 에러 메시지 원문 | X |
| 서버 내부 오류 | `INTERNAL` | Firebase SDK 에러 메시지 원문 | O |
| 전송 한도 초과 | `QUOTA_EXCEEDED` | Firebase SDK 에러 메시지 원문 | O |
| 서비스 불가 | `UNAVAILABLE` | Firebase SDK 에러 메시지 원문 | O |
| FCM 인증 실패 | `SENDER_ID_MISMATCH` | Firebase SDK 에러 메시지 원문 | O |
| 에러코드 null | `UNKNOWN` | 예외 메시지 원문 | O |
| 배치(500건) 전체 실패 | `BATCH_ERROR` | `FirebaseMessagingException` 메시지 | O |

**에러 분류 로직** (`FcmService.mapFcmError`):
```
UNREGISTERED, INVALID_ARGUMENT → 영구 실패 (재시도 불가, 토큰 무효화 대상)
그 외 모든 코드              → 일시 실패 (재시도 가능)
```

**참고**: 500개 토큰 단위로 배치 발송하며, 배치 내 **토큰마다** 개별적으로 성공/실패가 기록됩니다.

### 4.2 EMAIL

> 소스: `EmailService.java` — Spring `JavaMailSender` + SMTP 에러 메시지 파싱

| 상황 | `error_code` 값 | `error_message` 값 | 재시도 가능 |
|---|---|---|---|
| 발송 성공 | NULL | NULL | - |
| 이메일 주소가 비어있음 | `DELIVERY_016` | `이메일 주소가 누락되었습니다.` | X |
| 존재하지 않는 주소 (550/553) | `DELIVERY_010` | `MessagingException` 메시지 원문 | X |
| "Invalid Addresses" 포함 | `DELIVERY_010` | `MessagingException` 메시지 원문 | X |
| SMTP 서버 에러 등 기타 | `DELIVERY_009` | `MailException` 메시지 원문 | O |

**에러 분류 로직** (`EmailService.mapEmailError`):
```
에러 메시지에 "Invalid Addresses" or "550" or "553" 포함 → 영구 실패 (주소 자체가 무효)
그 외                                                 → 일시 실패 (서버 문제, 네트워크 등)
```

### 4.3 SMS

> 소스: `SmsService.java` — CoolSMS API (`api.coolsms.co.kr`) 에러 파싱

| 상황 | `error_code` 값 | `error_message` 값 | 재시도 가능 |
|---|---|---|---|
| 발송 성공 | NULL | NULL | - |
| 전화번호가 비어있음 | `DELIVERY_017` | `전화번호가 누락되었습니다.` | X |
| 잘못된 번호 형식 | `DELIVERY_012` | API 에러 메시지 원문 | X |
| CoolSMS 유효성 검증 실패 | `DELIVERY_012` | API 에러 메시지 원문 | X |
| API 호출 실패 (기타) | `DELIVERY_011` | 예외 메시지 원문 | O |

**에러 분류 로직** (`SmsService.mapSmsError`):
```
에러 메시지에 "InvalidPhoneNumber" or "ValidationError" 포함 → 영구 실패
그 외                                                      → 일시 실패
```

**참고**: 메시지 본문 EUC-KR 바이트 길이로 SMS(90바이트 이하) / LMS(초과)가 자동 결정됩니다.

### 4.4 WEBHOOK

> 소스: `WebhookService.java` — `RestClient` HTTP 호출 결과

| 상황 | `error_code` 값 | `error_message` 값 | 재시도 가능 |
|---|---|---|---|
| 발송 성공 | NULL | NULL | - |
| URL이 비어있음 | `DELIVERY_018` | `Webhook URL이 누락되었습니다.` | X |
| HTTP 에러 / 연결 실패 | `DELIVERY_013` | 예외 메시지 원문 (HTTP 상태 등) | O |

**참고**: Webhook은 `recipient`가 항상 NULL입니다. URL은 `channel_credentials` 테이블에 별도 저장됩니다.

---

## 5. `DeliveryResult` — 어댑터가 리턴하는 결과 구조체

```java
public record DeliveryResult(
    boolean successful,    // 성공 여부
    String errorCode,      // 에러 코드 (성공 시 null)
    String errorMessage,   // 에러 메시지 (성공 시 null)
    boolean retryable      // 재시도 가능 여부
)
```

| 팩토리 메서드 | successful | errorCode | errorMessage | retryable |
|---|---|---|---|---|
| `success()` | true | null | null | false |
| `permanentFailure(code, msg)` | false | 값 있음 | 값 있음 | **false** |
| `transientFailure(code, msg)` | false | 값 있음 | 값 있음 | **true** |

**DB 저장 매핑**:
- `successful=true` → `status=SENT`, error 컬럼 NULL
- `successful=false` → `status=FAILED`, `error_code`와 `error_message`에 값 저장

> `retryable` 값은 현재 DB에 저장되지 않습니다. 향후 재시도 로직 구현 시 활용될 필드입니다.

---

## 6. 상태 전이 — 발송 후 콜백

초기 INSERT 후 외부에서 콜백이 오면 상태가 갱신됩니다.

```
SENT ──→ DELIVERED ──→ CLICKED
  │
  └──→ FAILED
```

| 전이 | 트리거 | 갱신 컬럼 | 카운터 |
|---|---|---|---|
| SENT → DELIVERED | `markDelivered` 콜백 | `status`, `delivered_at` | `delivered_count++` |
| DELIVERED → CLICKED | `markClicked` 콜백 | `status`, `clicked_at` | `clicked_count++` |
| SENT → FAILED | `markFailed` 콜백 | `status`, `error_code`, `error_message` | - |

잘못된 전이 시도 시 `DELIVERY_002 (유효하지 않은 상태 전이)` 에러가 발생합니다.

---

## 7. 전체 에러 코드 목록

| 코드 | enum 이름 | 의미 |
|---|---|---|
| `DELIVERY_001` | `LOG_NOT_FOUND` | 발송 로그를 찾을 수 없음 |
| `DELIVERY_002` | `INVALID_STATUS_TRANSITION` | 유효하지 않은 상태 전이 |
| `DELIVERY_003` | `FCM_SEND_FAILED` | FCM 발송 실패 |
| `DELIVERY_004` | `FCM_CREDENTIALS_NOT_FOUND` | FCM 자격증명 미설정 |
| `DELIVERY_005` | `UNSUPPORTED_CHANNEL` | 지원하지 않는 채널 |
| `DELIVERY_006` | `CAMPAIGN_STATS_NOT_FOUND` | 캠페인 통계 없음 |
| `DELIVERY_007` | `EMPTY_TARGETS` | 발송 대상이 비어있음 |
| `DELIVERY_008` | `FCM_CREDENTIALS_INVALID` | FCM 자격증명 유효하지 않음 |
| `DELIVERY_009` | `EMAIL_SEND_FAILED` | 이메일 발송 실패 |
| `DELIVERY_010` | `EMAIL_INVALID_ADDRESS` | 유효하지 않은 이메일 주소 |
| `DELIVERY_011` | `SMS_SEND_FAILED` | SMS 발송 실패 |
| `DELIVERY_012` | `SMS_INVALID_NUMBER` | 유효하지 않은 전화번호 |
| `DELIVERY_013` | `WEBHOOK_SEND_FAILED` | Webhook 발송 실패 |
| `DELIVERY_014` | `WEBHOOK_URL_INVALID` | 유효하지 않은 Webhook URL |
| `DELIVERY_015` | `FCM_TOKEN_MISSING` | Push 토큰 누락 |
| `DELIVERY_016` | `EMAIL_ADDRESS_MISSING` | 이메일 주소 누락 |
| `DELIVERY_017` | `SMS_PHONE_MISSING` | 전화번호 누락 |
| `DELIVERY_018` | `WEBHOOK_URL_MISSING` | Webhook URL 누락 |
| `DELIVERY_019` | `CREDENTIAL_NOT_FOUND` | 채널 인증정보 없음 |

> **주의**: `error_code` 컬럼에는 위 코드(`DELIVERY_XXX`) 또는 **플랫폼 원본 코드**(FCM의 `UNREGISTERED`, `INVALID_ARGUMENT` 등)가 혼재합니다. FCM만 플랫폼 원본 코드를 직접 저장하고, 나머지 채널은 `DELIVERY_XXX` 코드를 사용합니다.

---

## 8. 소스 파일 위치

| 파일 | 역할 |
|---|---|
| `notification/service/DeliveryServiceImpl.java` | 발송 오케스트레이션 (Phase 1+2 분리) |
| `notification/service/DeliveryResultPersister.java` | @Transactional DB 저장 |
| `notification/service/fcm/FcmService.java` | FCM 배치 발송 + 에러 매핑 |
| `notification/service/email/EmailService.java` | 이메일 발송 + SMTP 에러 매핑 |
| `notification/service/sms/SmsService.java` | SMS(CoolSMS) 발송 + 에러 매핑 |
| `notification/service/webhook/WebhookService.java` | Webhook 발송 + HTTP 에러 매핑 |
| `notification/entity/NotificationLog.java` | 발송 로그 엔티티 (`@Entity(name = "SenderNotificationLog")`) |
| `notification/entity/NotificationStatus.java` | 상태 enum + 전이 규칙 |
| `notification/entity/CampaignStats.java` | 캠페인 실시간 카운터 |
| `notification/dto/response/DeliveryResult.java` | 발송 결과 record |
| `notification/exception/DeliveryErrorCode.java` | 에러 코드 enum (DELIVERY_001~019) |
| `notification/service/NotificationCallbackService.java` | 도달/클릭 콜백 처리 |
