# 시크릿 관리 & 환경별 설정 표준

> Owner: 이재영
> 최초 작성: 2026-05-11
> 관련 Jira: `S14P31B101-62` (환경별 설정 분리 및 시크릿 관리 표준화)
> 관련 문서: [db-permission-separation-manual.md](./db-permission-separation-manual.md), [backup-recovery-policy.md](./backup-recovery-policy.md), [log-policy.md](./log-policy.md)
> 관련 ADR: [ADR-0017](../adr/0017-public-api-key-authentication.md) Public API Key 인증 — SHA-256 해시 저장

본 문서는 Butterfly 의 **환경별 설정 분리 기준** 과 **시크릿 관리 표준** 을 정한다. 로컬·dev·stage·prod 환경의 설정이 섞이거나 민감 정보가 코드/이미지/로그에 노출되지 않도록 모든 팀원이 동일 기준을 따른다.

---

## 1. 환경 분류

| 환경 | 용도 | 설정 출처 | 시크릿 출처 |
|---|---|---|---|
| **로컬** (개발자 PC) | 일상 개발 | `backend/.env` (gitignore) | `.env` 평문 허용, 개인 책임 |
| **dev** (공유 개발 서버) | 통합 검증·QA Run | application-dev.yml + 환경 변수 | 시크릿 매니저 (`butterfly/dev/*`) |
| **stage** (데모 리허설용) | 발표 직전 검증 | application-stage.yml + 환경 변수 | 시크릿 매니저 (`butterfly/stage/*`) |
| **prod** (운영) | 외부 트래픽 수신 | application-prod.yml + 환경 변수 | 시크릿 매니저 (`butterfly/prod/*`) + KMS 분리 |

> v1 (MVP) 에서는 stage/prod 가 동일 컨테이너일 수 있음. 환경 변수 + 시크릿 키 네이밍 만 분리.

### 환경 선택 방법

Spring Boot 의 `spring.profiles.active` 로 환경 선택:

```bash
# 로컬
SPRING_PROFILES_ACTIVE=api,worker ./gradlew :butterfly-api:bootRun

# dev (CI/CD 또는 컨테이너 환경 변수)
SPRING_PROFILES_ACTIVE=dev,api,worker
```

`api` / `worker` 프로파일은 항상 활성. 그 위에 `dev`/`stage`/`prod` 가 환경별 차이를 덮어쓴다 (계획 — v1 에서는 환경 변수 override 만 사용).

---

## 2. 설정 vs 시크릿 분리 기준

### 설정 (Configuration) — `application.yml` 에 평문 저장 가능

- 서버 호스트·포트 (기본값 `localhost`)
- Spring 프로파일·application name
- 로깅 레벨·로그 패턴
- Hibernate ddl-auto, Flyway 설정
- Pageable 최대 페이지 크기
- CORS allowed origins (운영은 도메인 명시, 기본값은 로컬 SPA 포트)
- Rate limit 정책값 (RPM, burst)
- KPI 정의서 정책값 (attribution 윈도우 30일 등 — KPI 정의서 §4.2)

### 시크릿 (Secret) — **절대 코드/이미지/로그에 평문 노출 금지**

- DB 자격 증명 (`DB_USERNAME`, `DB_PASSWORD`)
- Redis 비밀번호 (사용 시)
- Kafka SASL 자격 증명 (사용 시)
- JWT 서명 키 (`JWT_SECRET`)
- 메일 SMTP 비밀번호 (`MAIL_PASSWORD`)
- 채널 자격 증명 (FCM service account JSON, APNS 인증서, SMS 게이트웨이 API Key 등)
- 외부 API Key (Sentry, DataDog 등 도입 시)
- 시크릿 매니저 자체 접근용 IAM 자격 증명 (KMS 키)

### 의심스러우면 시크릿으로 취급

회색 영역 (예: 이메일 발신 주소 같이 노출돼도 큰 손해 없는 값) 도 일관성 위해 같은 채널로 주입. 로그에 평문 출력만 막으면 됨.

---

## 3. 환경 변수 카탈로그

application.yml 에서 참조하는 모든 환경 변수의 전체 목록. **신규 환경 변수 추가 시 본 표 + `.env.example` 동시 갱신 필수**.

### 3.1 인프라 접속 정보

| 변수명 | 기본값 | 시크릿? | 비고 |
|---|---|---|---|
| `DB_HOST` | `localhost` | ❌ | docker-compose 기본 |
| `DB_PORT` | `3307` | ❌ | 로컬 docker MySQL |
| `DB_NAME` | `butterfly` | ❌ | |
| `DB_USERNAME` | (없음, 필수) | ⚠️ | 로컬 root, dev+ 는 app_user (S14P31B101-182) |
| `DB_PASSWORD` | (없음, 필수) | ✅ | 시크릿 매니저 회수 |
| `REDIS_HOST` | `localhost` | ❌ | |
| `REDIS_PORT` | `6379` | ❌ | |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | ❌ | |

### 3.2 인증·암호화

| 변수명 | 기본값 | 시크릿? | 비고 |
|---|---|---|---|
| `JWT_SECRET` | (없음, 필수) | ✅ | 최소 32자, `openssl rand -base64 32` 권장 |

> ⚠️ `AES_KEY` 는 S14P31B101-206 으로 AesEncryptionService 가 제거되면서 미사용. 본 PR 에서 application.yml 잔재 정리. FCM 자격 증명 암호화는 Phase 2 별도 결정.

### 3.3 메일

| 변수명 | 기본값 | 시크릿? | 비고 |
|---|---|---|---|
| `MAIL_HOST` | `localhost` | ❌ | 로컬 MailHog |
| `MAIL_PORT` | `1025` | ❌ | |
| `MAIL_USERNAME` | (빈 문자열) | ⚠️ | 메일 발송자 식별 정보 |
| `MAIL_PASSWORD` | (없음, 필수) | ✅ | SMTP 비밀번호 |
| `MAIL_FROM` | (없음, 필수) | ⚠️ | 발신 주소 |
| `MAIL_BASE_URL` | `http://localhost:5175` | ❌ | 메일 본문 링크의 base URL |

### 3.4 정책값 (운영 튜닝)

| 변수명 | 기본값 | 시크릿? | 비고 |
|---|---|---|---|
| `BUTTERFLY_CORS_ALLOWED_ORIGINS` | 로컬 SPA 포트 5종 | ❌ | 운영은 도메인 명시 |
| `BUTTERFLY_RATELIMIT_ENABLED` | `true` | ❌ | |
| `BUTTERFLY_RATELIMIT_RPM` | `60` | ❌ | |
| `BUTTERFLY_RATELIMIT_BURST` | `100` | ❌ | |

---

## 4. 시크릿 매니저 키 네이밍 표준

운영(dev/stage/prod) 시크릿은 다음 네이밍을 강제:

```
butterfly/{env}/{component}
```

| env | component | 예시 키 | 내용 |
|---|---|---|---|
| `dev` / `stage` / `prod` | `app_user` | `butterfly/dev/app_user` | DB app_user 비밀번호 (S14P31B101-182) |
| 위 | `audit_viewer` | `butterfly/dev/audit_viewer` | (선택) audit_viewer 비밀번호 |
| 위 | `jwt` | `butterfly/dev/jwt` | JWT 서명 시크릿 |
| 위 | `mail-smtp` | `butterfly/dev/mail-smtp` | SMTP 비밀번호 |
| 위 | `fcm-credentials` | `butterfly/dev/fcm-credentials` | FCM service account JSON (Phase 2) |
| 위 | `apns-cert` | `butterfly/dev/apns-cert` | APNS 인증서 (Phase 2) |

### KMS 분리

prod 환경의 시크릿은 별도 KMS 키 (예: `arn:aws:kms:.../butterfly-prod`) 로 암호화. 권한:

- 접근 가능: 운영 책임자 1~2명 + 애플리케이션 IAM 역할
- KMS 키 분실 = 모든 시크릿 분실 → 백업 정책 §4.5 참고

### 키 회수 방법

```bash
# AWS Secrets Manager 예시
aws secretsmanager get-secret-value \
  --secret-id butterfly/dev/app_user \
  --query SecretString --output text

# 권한 없는 경우 운영 책임자에게 1:1 요청 (절대 채널에 평문 공유 금지)
```

---

## 5. 환경별 적용 절차

### 5.1 로컬 (개발자 PC)

1. `backend/.env.example` 복사 → `backend/.env`
2. 각 값을 본인 로컬 환경에 맞게 채움 (DB는 docker-compose 기본 `root/root`, JWT 는 평문 32자 dev 키)
3. IDE 실행 시 `backend/.env` 자동 로드 (Spring Boot `spring-dotenv` 사용 또는 IDE plugin)
4. `./gradlew :butterfly-api:bootRun` 또는 IDE 에서 Application Run

**금지**:
- 실제 운영 비밀번호를 `backend/.env` 에 넣지 않음
- `backend/.env` commit 금지 (gitignore 됨)
- 개인 이메일 SMTP 비밀번호 같은 진짜 시크릿은 .env 에 둘 수 있으나, **PC 공유/원격 접근 환경에서는 OS 키체인 권장**

### 5.2 dev / stage / prod (배포 환경)

1. 시크릿 매니저에서 시크릿 회수 (CI/CD 또는 컨테이너 entrypoint 에서)
2. 환경 변수로 주입 (Kubernetes Secret, AWS ECS task definition `secrets:`, docker-compose `env_file:` 등)
3. Spring Boot 가 application.yml 의 `${VAR}` placeholder 를 환경 변수로 resolve
4. 로그에 시크릿이 평문으로 출력되지 않는지 확인 ([log-policy.md §4](./log-policy.md))

**예시 — Kubernetes Secret + Deployment**

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: butterfly-dev-secrets
type: Opaque
stringData:
  DB_PASSWORD: __from_aws_secrets_manager__   # ExternalSecrets Operator 또는 init container
  JWT_SECRET: __from_aws_secrets_manager__
  MAIL_PASSWORD: __from_aws_secrets_manager__
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: butterfly-api-dev
spec:
  template:
    spec:
      containers:
        - name: api
          envFrom:
            - secretRef:
                name: butterfly-dev-secrets
            - configMapRef:
                name: butterfly-dev-config   # 비밀이 아닌 설정
```

---

## 6. 신규 환경 변수 추가 절차

새 시크릿/설정이 필요할 때:

1. **시크릿 여부 판단** — §2 기준 (의심스러우면 시크릿)
2. **application.yml 에 `${VAR_NAME}` placeholder 추가** (필수 변수는 기본값 없음, 선택 변수는 `${VAR:default}`)
3. **`backend/.env.example` 에 항목 추가** — placeholder 값으로 (`__CHANGE_ME__` 또는 `__GENERATE_ME__`)
4. **본 문서 §3 카탈로그에 행 추가** — 시크릿 여부, 기본값, 비고
5. **시크릿이면 시크릿 매니저 키 등록** — §4 네이밍 표준 따라
6. **PR 시 본 문서 갱신** — 카탈로그 정합성 유지

> **금지**: `.env.example` 또는 application.yml 에 실제 운영 비밀번호를 평문으로 넣고 commit. 발견 시 git history rewrite + 비밀번호 즉시 회전.

---

## 7. 트러블슈팅

### 7.1 `IllegalArgumentException: Could not resolve placeholder 'DB_PASSWORD'`

환경 변수 미설정. 로컬:

```bash
# .env 가 있는지 + 값이 채워져 있는지
ls backend/.env && grep DB_PASSWORD backend/.env

# IDE 의 spring-dotenv 또는 EnvFile plugin 활성화 여부
```

운영:

- 시크릿 매니저 회수 단계 (CI/CD 또는 init container) 실패 가능성
- Pod/Container 환경 변수 직접 확인 (`kubectl exec ... env | grep DB_PASSWORD`)

### 7.2 로그에 평문 시크릿이 찍힘

가장 위험. [log-policy.md §4 마스킹 규칙](./log-policy.md) 위반.

- 즉시 해당 시크릿 회전 (시크릿 매니저 + 모든 환경 갱신)
- 로그 수집기(Loki/ELK)에서 평문 검색 → 30일 보존 로그 보유 시 영향 범위 파악
- 코드 수정: 평문 출력 위치 fix + 마스킹 헬퍼 도입

### 7.3 환경 변수가 application.yml 의 평문값을 덮어쓰지 못함

Spring Boot 의 환경 변수 → application.yml placeholder 매핑은 다음 순서:

1. OS 환경 변수 (가장 높음)
2. `application-{profile}.yml`
3. `application.yml`
4. 코드 내 `@Value` default

→ OS 환경 변수가 우선이므로 dev/stage/prod 에서 강제로 override 가능. 안 되면 변수명 정확성 확인 (대소문자, underscore vs dot).

---

## 8. v1 → Phase 2 계획

| 항목 | v1 (현재) | Phase 2 |
|---|---|---|
| 시크릿 저장소 | AWS Secrets Manager (가정) | 동일 + KMS 강제 + 자동 회전 |
| 환경별 yml | application.yml 단일 + 환경 변수 override | application-{env}.yml 분리 (필요 시) |
| 채널 자격 증명 (FCM/APNS) | 미도입 | 시크릿 매니저 + KMS 별도 |
| AES 암호화 (DB 컬럼 레벨) | 미사용 (S14P31B101-206 으로 제거) | 필요 시 재도입 (KMS Envelope) |
| 시크릿 회전 정책 | 수동 | 자동 (90일 권장) |

본 문서는 Phase 2 진입 시점에 갱신.

---

## 9. 변경 이력

| 날짜 | 변경 | 작성자 |
|---|---|---|
| 2026-05-11 | 초안 작성 (S14P31B101-62), AES_KEY 제거 정리, .env.example 신규 추가 | 이재영 |
| TBD | Phase 2 진입 시 갱신 | 후속 작업자 |
