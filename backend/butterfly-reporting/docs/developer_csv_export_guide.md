# CSV 내보내기 API + 다른 리포트에 적용하기 — 개발자 가이드

> **대상**: 본 API를 호출하는 프론트엔드 / 다른 리포트(34/92/90)에 CSV를 추가하려는 백엔드
> **티켓**: [S14P31B101-91](https://ssafy.atlassian.net/browse/S14P31B101-91), [S14P31B101-35](https://ssafy.atlassian.net/browse/S14P31B101-35)
> **작성**: 2026-05-07

코드와 함께 보는 실용 가이드. 카피-페이스트로 시작할 수 있게 스니펫 위주.

---

## 1. API 스펙 — 감사 로그 CSV

### 1.1 엔드포인트

```
GET /api/v1/audit-logs/export
```

### 1.2 권한

OWNER 권한 필요 (`@projectPermission.hasRole(#projectId, 'OWNER')`).

### 1.3 쿼리 파라미터

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `projectId` | long | ❌ | 프로젝트 필터 |
| `memberId` | long | ❌ | 행위자 필터 |
| `action` | string | ❌ | 액션 코드 필터 (예: `FCM_KEY_UPDATE`) |
| `targetType` | string | ❌ | 대상 타입 필터 (예: `Project`) |
| `startDate` | ISO date | ❌ | 시작일 (포함) |
| `endDate` | ISO date | ❌ | 종료일 (포함) |

조회 API(`GET /api/v1/audit-logs`)와 **완전히 동일한 필터셋**을 받는다. 화면에서 동일 필터 → "다운로드" 버튼만 누르면 됨.

### 1.4 헤더 (request)

| 이름 | 필수 | 설명 |
|---|---|---|
| `Authorization` | ✅ | JWT 토큰 |
| `X-Member-Id` | ✅ | 행위자 ID — self-audit 기록용. auth 모듈 합류 시 `@AuthenticationPrincipal`로 자동화 예정 |

### 1.5 응답

| 케이스 | 응답 |
|---|---|
| 정상 | `200 OK` + `text/csv;charset=UTF-8` body |
| from > to | `400 ANALYTICS_002 INVALID_DATE_RANGE` |
| 매칭 row > 50,000 | `400 ANALYTICS_003 DATE_RANGE_TOO_LARGE` |
| 권한 없음 | `403` |

#### 응답 헤더
```
Content-Type: text/csv;charset=UTF-8
Content-Disposition: attachment; filename*=UTF-8''audit-logs-20260507-131800.csv
Cache-Control: no-store
```

#### 응답 본문
```
[BOM 3바이트]
auditId,projectId,memberId,action,targetType,targetId,ipAddress,userAgent,createdAt,detail
1,100,200,FCM_KEY_UPDATE,Project,100,127.0.0.1,test-agent,2026-05-01T12:00:00,"{""hashPrefix"":""a1b2c3d4""}"
2,...
```

---

## 2. 호출 예시

### 2.1 cURL

```bash
# 프로젝트 1의 4월 한 달 감사 로그 다운로드
curl -H "Authorization: Bearer $TOKEN" \
     -H "X-Member-Id: 999" \
     -OJ \
     "https://api.butterfly.com/api/v1/audit-logs/export?projectId=1&startDate=2026-04-01&endDate=2026-04-30"

# 특정 액션만
curl -H "Authorization: Bearer $TOKEN" \
     -H "X-Member-Id: 999" \
     -OJ \
     "https://api.butterfly.com/api/v1/audit-logs/export?action=FCM_KEY_UPDATE"
```

`-OJ` — Content-Disposition의 filename으로 저장.

### 2.2 프론트 — 브라우저 다운로드 트리거

```ts
import axios from 'axios';

export async function downloadAuditLogCsv(filters: AuditLogFilters) {
  const response = await axios.get('/api/v1/audit-logs/export', {
    params: filters,
    responseType: 'blob',
    headers: { 'X-Member-Id': currentMemberId() },
  });

  // Content-Disposition에서 파일명 추출
  const disposition = response.headers['content-disposition'] ?? '';
  const filename = parseFilenameFromDisposition(disposition) ?? 'audit-logs.csv';

  const url = URL.createObjectURL(response.data);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}

function parseFilenameFromDisposition(value: string): string | null {
  // RFC 5987 — filename*=UTF-8''<encoded>
  const match = value.match(/filename\*=UTF-8''([^;]+)/i);
  return match ? decodeURIComponent(match[1]) : null;
}
```

**중요**:
- `responseType: 'blob'` 필수 — JSON으로 파싱하면 안 됨
- 50k 초과 응답은 `400` JSON이 옴 — error handler에서 blob을 텍스트로 읽어 메시지 추출 필요
- 다운로드 시작 후 실패 시 사용자 알림 — 빈 파일 받지 않게 try/catch

### 2.3 50k 초과 케이스 핸들링

```ts
try {
  await downloadAuditLogCsv(filters);
} catch (e) {
  if (axios.isAxiosError(e) && e.response?.status === 400) {
    // 400 응답은 blob → text → JSON으로 풀어 errorCode 확인
    const errorJson = JSON.parse(await (e.response.data as Blob).text());
    if (errorJson.code === 'ANALYTICS_003') {
      alert('필터 조건이 너무 넓어 50,000건을 넘습니다. 기간/조건을 좁혀 주세요.');
      return;
    }
  }
  throw e;
}
```

---

## 3. CsvWriter — 다른 도메인에서 쓰기

`reporting.export.CsvWriter`는 도메인 무관 공용. 다른 리포트가 그대로 가져다 쓸 수 있다.

### 3.1 기본 사용

```java
import com.b101.butterfly.reporting.export.CsvWriter;

StringWriter out = new StringWriter();
CsvWriter csv = new CsvWriter(out);

csv.writeHeader(List.of("id", "name", "note"));
csv.writeRow(List.of("1", "Alice", "hello, world"));   // 쉼표는 자동 escape
csv.writeRow(List.of("2", "Bob", "say \"hi\""));        // 따옴표는 ""로 escape
csv.flush();

// 결과:
// id,name,note
// 1,Alice,"hello, world"
// 2,Bob,"say ""hi"""
```

### 3.2 BOM은 호출자 책임

CsvWriter는 BOM을 출력하지 않는다 — sink(HTTP/파일/Kafka)에 따라 정책이 다를 수 있어서.

HTTP 응답에 직접 쓸 때:
```java
import com.b101.butterfly.reporting.audit.service.AuditLogCsvExportService;

PrintWriter writer = response.getWriter();
writer.write(AuditLogCsvExportService.UTF8_BOM);  // 첫 바이트 BOM
CsvWriter csv = new CsvWriter(writer);
csv.writeHeader(...);
csv.writeRow(...);
```

`UTF8_BOM` 상수는 `AuditLogCsvExportService`에 public으로 노출되어 있다 — 다른 export 서비스도 그대로 import해서 쓰면 됨. (CsvWriter로 옮길까 검토했지만 transport-level 결정이라 분리 유지.)

### 3.3 null cell

`writeRow`의 `List<String>`에 `null`이 있으면 빈 cell로 출력 (`,,`). 일관된 동작.

```java
csv.writeRow(Arrays.asList("a", null, "c"));  // "a,,c\n"
```

`List.of()`는 null을 못 받으니 `Arrays.asList`나 `ArrayList` 사용.

---

## 4. 새 리포트에 CSV 추가하기 — 4단계 체크리스트

`S14P31B101-34` (캠페인 성과), `S14P31B101-92` (매트릭스), `S14P31B101-90` (펀널) 등 다른 리포트에 CSV를 추가하려면:

### Step 1 — RowMapper 작성

```java
// 예: CampaignReportCsvRow
final class CampaignReportCsvRow {

    static final List<String> HEADERS = List.of(
            "campaignId", "campaignName", "channel",
            "sentCount", "deliveredCount", "openCount",
            "clickCount", "convertCount",
            "openRate", "clickRate", "convertRate"
    );

    static List<String> toRow(CampaignReportRow row) {
        return Arrays.asList(
                str(row.campaignId()),
                row.campaignName(),
                row.channel().name(),
                String.valueOf(row.sentCount()),
                // ...
                rateStr(row.openRate())
        );
    }

    private static String str(Long v) { return v == null ? "" : v.toString(); }
    private static String rateStr(BigDecimal v) { return v == null ? "" : v.toPlainString(); }
}
```

### Step 2 — Export Service 작성

`AuditLogCsvExportService`를 그대로 복사한 후:
- Repository를 본인 도메인 것으로 교체
- HEADERS와 toRow를 본인 RowMapper로 교체
- `EXPORT_ACTION` 상수를 본인 액션 코드로 교체 (`CAMPAIGN_REPORT_EXPORT` 등)
- `targetType`을 본인 도메인 타입으로 교체 (`"CampaignReport"` 등)

대부분 변경은 5~10줄. 재사용되는 핵심 로직(streaming, 임계, self-audit 발행)은 그대로.

### Step 3 — Controller에 endpoint 추가

```java
@GetMapping(value = "/export", produces = "text/csv;charset=UTF-8")
@PreAuthorize("@projectPermission.hasRole(#projectId, 'OWNER')")
public void exportCsv(
        @PathVariable Long projectId,
        @RequestParam ... /* 조회 endpoint와 동일 필터 */,
        @RequestHeader("X-Member-Id") Long actorMemberId,
        HttpServletRequest request,
        HttpServletResponse response
) throws IOException {
    response.setContentType("text/csv;charset=UTF-8");
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setHeader("Content-Disposition",
            "attachment; filename*=UTF-8''" + buildFilename("campaign-report"));
    response.setHeader("Cache-Control", "no-store");

    PrintWriter writer = response.getWriter();
    campaignReportCsvExportService.exportToCsv(
            query, writer, actorMemberId,
            clientIp(request), request.getHeader("User-Agent"));
}
```

`AuditLogController.buildFilename` / `clientIp`을 그대로 가져와 쓰면 됨.

### Step 4 — 테스트

`AuditLogCsvExportServiceTest`/`CsvWriterTest` 패턴 복사:
- Mockito로 repository mock
- TransactionTemplate fake 함수 그대로 (`fakeTransactionTemplate()`)
- 검증 항목:
  - validate 거부
  - 임계 초과 거부
  - BOM + 헤더 + row 출력
  - 자체 audit 이벤트 발행 (action, actor, ip, ua, exportedCount 검증)
  - 특수문자 이스케이프
  - actor null 방어
  - 페이지 streaming (1001건 케이스로 2페이지 호출 검증)

---

## 5. 자주 묻는 질문

### Q1. CSV 다운로드 자체가 audit_logs에 어떻게 기록되나?

`AUDIT_LOG_EXPORT` 액션으로 다음과 같이 기록:
```json
{
  "action": "AUDIT_LOG_EXPORT",
  "memberId": 999,
  "targetType": "AuditLog",
  "ipAddress": "10.0.0.1",
  "userAgent": "Mozilla/...",
  "detail": {
    "filter": { "projectId": 100, "startDate": "2026-04-01", "endDate": "2026-04-30" },
    "exportedCount": 1234
  }
}
```

운영자가 누가 언제 어떤 조건으로 몇 건 반출했는지 추적 가능.

### Q2. AFTER_COMMIT listener가 실패하면 응답이 깨지나?

아니요. `@TransactionalEventListener(phase = AFTER_COMMIT)`은 발행자 트랜잭션이 commit된 후에 호출되며, listener 예외는 발행자에게 전파되지 않는다. 즉:
- CSV 응답은 정상 200으로 완료
- self-audit 저장만 실패
- 운영 모니터링으로 잡아야 함 (로그 + 추후 metric)

이게 의도된 설계 — audit이 비즈니스 로직(CSV 다운로드)을 깨뜨리면 안 된다.

### Q3. detail에 한글이 들어가도 괜찮나?

YES. UTF-8 + BOM이라 한글 안전. JSON 직렬화도 UTF-8 기본이라 escape 없이 그대로 들어간다.

```csv
"{""변경필드"":""한글값""}"
```

### Q4. 50k 임계는 어떻게 정했나?

추정치. 메모리 + 응답 시간 trade-off:
- 50k row × 평균 200 byte ≈ 10MB body — 다운로드 시간 OK
- DB SUM count 쿼리 + 50번 페이지 fetch ≈ 1~3초

운영 측정 후 조정 필요. `MAX_SYNC_EXPORT_ROWS` 상수 변경.

### Q5. CSV 안의 큰 detail JSON이 cell 한 개로 들어가는데 Excel이 잘 여나?

YES. Excel은 큰따옴표 escape를 정상 처리. 단, cell 안의 줄바꿈이 있을 때 일부 구버전 Excel이 깨질 수 있어 detail JSON에는 줄바꿈을 넣지 않도록 주의 (현재 ObjectMapper default가 줄바꿈 없는 한 줄 출력).

### Q6. `produces = "text/csv;charset=UTF-8"`인데 실제 응답에는 별도로 setContentType을 또 부르는 이유?

`@GetMapping(produces = ...)`는 content negotiation에 사용 (Accept 헤더와 매칭). 실제 응답 헤더는 `response.setContentType()`로 명시해야 client가 확실히 CSV로 인식. 둘 다 있는 게 안전.

### Q7. 다른 도메인에서 CsvWriter를 쓰려면 reporting 모듈에 의존해야 하는데, ADR-0014 모듈 경계 위반 아닌가?

현재 상황:
- 본 PR은 첫 사용처(audit). reporting 안에 두는 게 자연스러움.
- 다른 리포트(34/92/90)도 모두 reporting 모듈 안. → 경계 위반 없음.
- 다른 도메인(예: campaign, segment)이 직접 CSV를 쓰면 → 그 시점에 `butterfly-common`으로 승격 검토.

지금 옮기면 사용처 1군데를 위해 common이 무거워짐. 첫 외부 사용자 등장 시 옮긴다.

---

## 6. 관련 코드 빠른 참조

| 파일 | 역할 |
|---|---|
| [CsvWriter.java](../src/main/java/com/b101/butterfly/reporting/export/CsvWriter.java) | RFC 4180 직렬화 (도메인 무관) |
| [AuditLogCsvRow.java](../src/main/java/com/b101/butterfly/reporting/audit/service/AuditLogCsvRow.java) | AuditLog → cells 변환 |
| [AuditLogCsvExportService.java](../src/main/java/com/b101/butterfly/reporting/audit/service/AuditLogCsvExportService.java) | streaming + 임계 + self-audit |
| [AuditLogController.exportCsv](../src/main/java/com/b101/butterfly/reporting/audit/controller/AuditLogController.java) | HTTP endpoint |
| [CsvWriterTest.java](../src/test/java/com/b101/butterfly/reporting/export/CsvWriterTest.java) | RFC 4180 단위 테스트 11건 |
| [AuditLogCsvExportServiceTest.java](../src/test/java/com/b101/butterfly/reporting/audit/service/AuditLogCsvExportServiceTest.java) | streaming + audit 발행 테스트 6건 |
