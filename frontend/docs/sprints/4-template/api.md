# Sprint 4 — Message Template API 명세

> Backend Liaison Agent가 정리한 백엔드 → 프론트엔드 계약서.  
> **작성 일시**: 2026-05-01  
> **백엔드 버전**: Spring Boot 3.5.13 (butterfly-campaign)  
> **상태**: POST /api/v1/templates ✅ 구현 완료 | GET/PUT/DELETE ❌ 미구현

---

## 1. 도메인 개요 및 권한 매트릭스

### 1.1 Message Template 도메인 범위

Message Template는 메시지 발송 템플릿 관리 도메인입니다.
Sprint 4에서는 다음 기능을 대상:
- 템플릿 생성, 목록/상세/수정/삭제
- {{variable}} 패턴 자동 추출
- Soft delete (논리 삭제)

포함 엔드포인트:
- API 1: POST `/api/v1/templates` — 생성 ✅
- API 2: GET `/api/v1/templates` — 목록 ❌ 미구현
- API 3: GET `/api/v1/templates/{id}` — 상세 ❌ 미구현
- API 4: PUT `/api/v1/templates/{id}` — 수정 ❌ 미구현
- API 5: DELETE `/api/v1/templates/{id}` — 삭제 ❌ 미구현

### 1.2 권한 매트릭스

| 엔드포인트 | EDITOR | OWNER | ADMIN |
|---|:---:|:---:|:---:|
| POST /api/v1/templates | ✅ | ✅ | ✅ |
| GET /api/v1/templates | ✅ | ✅ | ✅ |
| GET /api/v1/templates/{id} | ✅ | ✅ | ✅ |
| PUT /api/v1/templates/{id} | ✅ | ✅ | ✅ |
| DELETE /api/v1/templates/{id} | ✅ | ✅ | ✅ |

**권한 검증**: @PreAuthorize 미적용 (Sprint 5 예정).  
프론트엔드에서 EDITOR 이상만 templates 탭 접근 가능하도록 제어.

---

## 2. 공통 사항

### 2.1 응답 Envelope

모든 응답은 ApiResponse<T> 래퍼:

```json
{
  "success": true,
  "data": { },
  "error": null
}
```

실패 시:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "TEMPLATE_NAME_REQUIRED",
    "message": "템플릿 이름은 필수입니다"
  }
}
```

### 2.2 임시 헤더: X-Member-Id

Sprint 4는 인증 시스템 완성 전 단계.
memberId는 X-Member-Id 헤더로 전달:

```
X-Member-Id: 1
```

### 2.3 기본 정보

- 기본 URL: `http://localhost:8080`
- Content-Type: `application/json`
- 성공: 201 Created (생성) / 200 OK (조회/수정/삭제)
- 실패: 400 Bad Request, 404 Not Found, 500 Internal Server Error

---

## 3. 구현된 API

### 3.1 POST /api/v1/templates — 템플릿 생성 ✅

**상태**: 구현 완료

#### 요청

```http
POST /api/v1/templates
Content-Type: application/json
X-Member-Id: 1

{
  "projectId": 1,
  "messageTemplateName": "환영 메시지",
  "channel": "SMS",
  "messageTemplateBody": "안녕하세요 {{name}}님! 환영합니다."
}
```

#### 요청 DTO: MessageTemplateCreateRequest

| 필드 | 타입 | 필수 | 설명 |
|---|---|:---:|---|
| projectId | Long | ✅ | 프로젝트 ID |
| messageTemplateName | String | ✅ | 템플릿 이름 (@NotBlank) |
| channel | String | ✅ | 채널: PUSH, EMAIL, SMS, WEBHOOK, KAKAO |
| messageTemplateBody | String | ✅ | 메시지 본문 |

#### 응답

```json
{
  "success": true,
  "data": {
    "messageTemplateId": 1
  },
  "error": null
}
```

#### 응답 DTO: MessageTemplateCreateResponse

| 필드 | 타입 |
|---|---|
| messageTemplateId | Long |

#### 상태 코드

- 201 Created: 성공
- 400 Bad Request:
  - TEMPLATE_NAME_REQUIRED: 이름 누락
  - TEMPLATE_PROJECT_REQUIRED: projectId 누락
  - TEMPLATE_MEMBER_REQUIRED: memberId 누락
  - TEMPLATE_CHANNEL_REQUIRED: channel 누락
  - TEMPLATE_BODY_REQUIRED: messageTemplateBody 누락

#### 자동 처리

백엔드가 messageTemplateBody에서 {{name}} 같은 변수 자동 추출:
```
MessageTemplateVariableExtractor.extract(body)
결과: ["name"]
저장: required_variables JSON 필드
```

---

## 4. 미구현 API (Mock Fallback 권장)

### 4.1 GET /api/v1/templates — 목록 조회 ❌

**상태**: 미구현  
**Mock 플래그**: `template_list: true` (src/api/_mock/index.ts)

#### 제안 시그니처

```http
GET /api/v1/templates?page=0&size=10&projectId=1
X-Member-Id: 1
```

#### 제안 응답

```json
{
  "success": true,
  "data": {
    "templates": [
      {
        "messageTemplateId": 1,
        "messageTemplateName": "환영 메시지",
        "channel": "SMS",
        "createdBy": "user@example.com",
        "createdAt": "2026-05-01T10:30:00Z",
        "status": "DRAFT"
      }
    ],
    "totalPages": 1,
    "totalElements": 1,
    "currentPage": 0
  },
  "error": null
}
```

---

### 4.2 GET /api/v1/templates/{id} — 상세 조회 ❌

**상태**: 미구현

#### 제안 시그니처

```http
GET /api/v1/templates/1
X-Member-Id: 1
```

#### 제안 응답

```json
{
  "success": true,
  "data": {
    "messageTemplateId": 1,
    "projectId": 1,
    "messageTemplateName": "환영 메시지",
    "channel": "SMS",
    "messageTemplateBody": "안녕하세요 {{name}}님!",
    "requiredVariables": ["name"],
    "status": "DRAFT",
    "createdBy": "user@example.com",
    "createdAt": "2026-05-01T10:30:00Z"
  },
  "error": null
}
```

---

### 4.3 PUT /api/v1/templates/{id} — 수정 ❌

**상태**: 미구현

#### 제안 시그니처

```http
PUT /api/v1/templates/1
Content-Type: application/json
X-Member-Id: 1

{
  "messageTemplateName": "환영 (수정)",
  "channel": "SMS",
  "messageTemplateBody": "안녕하세요!"
}
```

**제약**: READY 이상 상태에서는 수정 불가.

---

### 4.4 DELETE /api/v1/templates/{id} — 삭제 ❌

**상태**: 미구현

#### 제안 시그니처

```http
DELETE /api/v1/templates/1
X-Member-Id: 1
```

#### 응답

```json
{
  "success": true,
  "data": { "messageTemplateId": 1 },
  "error": null
}
```

**제약**: Soft delete 사용, READY 이상에서는 삭제 불가.

---

## 5. 템플릿 상태 & 변수

### 5.1 템플릿 상태

현재 MessageTemplate에 status 필드 없음. 향후 추가 예상:

| 상태 | 수정 | 삭제 |
|---|:---:|:---:|
| DRAFT | ✅ | ✅ |
| READY | ❌ | ❌ |
| ARCHIVED | ❌ | ❌ |

### 5.2 변수 자동 추출

입력: "안녕하세요 {{name}}님! {{company}}에 오셨습니다."
추출: ["name", "company"]

정규식: `\{\{(\w+)\}\}`

---

## 6. 이미지 처리

### 6.1 현재 상태

MessageTemplateCreateRequest에 이미지 필드 없음.
multipart/form-data 미구현.

### 6.2 향후 옵션

**Option A**: Base64 문자열
**Option B**: 외부 URL (추천)
**Option C**: Multipart 업로드

프론트엔드 압축: 64x64, 128x128, 256x256, 512x512.

---

## 7. 에러 코드

| 코드 | HTTP | 설명 |
|---|:---:|---|
| TEMPLATE_NAME_REQUIRED | 400 | 이름 누락 |
| TEMPLATE_PROJECT_REQUIRED | 400 | projectId 누락 |
| TEMPLATE_MEMBER_REQUIRED | 400 | memberId 누락 |
| TEMPLATE_CHANNEL_REQUIRED | 400 | channel 누락 |
| TEMPLATE_BODY_REQUIRED | 400 | messageTemplateBody 누락 |
| TEMPLATE_NOT_FOUND | 404 | 템플릿 없음 (향후) |
| TEMPLATE_ALREADY_SENT | 400 | READY 이상 수정/삭제 (향후) |

---

## 8. UI 매핑

### 생성 Form
```
[텍스트] 템플릿 이름
[셀렉트] 채널 (PUSH, EMAIL, SMS, WEBHOOK, KAKAO)
[텍스트 에어리어] 메시지 본문
힌트: "{{변수명}} 형식 사용 가능"
```

### 목록 테이블
| 이름 | 채널 | 생성자 | 생성일 | 상태 |
|---|---|---|---|---|

### 상세 View
```
[읽기 전용] 이름, 채널, 상태, 생성일, 생성자
[편집 가능/DRAFT만] 메시지 본문, 변수 목록
```

---

## 9. 마이그레이션

### X-Member-Id 제거 (Spring Security 적용 시)

현재:
```typescript
api.post('/api/v1/templates', data, {
  headers: { 'X-Member-Id': memberId }
})
```

향후:
```typescript
api.post('/api/v1/templates', data)
```

---

## 10. 참고 자료

- 백엔드 소스: `/butterfly-campaign/src/main/java/com/b101/butterfly/campaign/messagetemplate/`
- 프론트엔드 mock: `/frontend/src/api/_mock/index.ts`
- ApiResponse: `/butterfly-common/global/response/ApiResponse.java`

**다음 Sprint**:
- Sprint 5: GET 목록/상세 구현
- Sprint 6: PUT/DELETE + 권한 검증