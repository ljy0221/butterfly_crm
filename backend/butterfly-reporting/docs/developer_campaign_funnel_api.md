# 캠페인 단계 펀널 API — 개발자 가이드

> **대상**: 본 API를 호출하는 프론트엔드 / 다른 백엔드 도메인 / 비슷한 펀널을 자기 도메인에 도입하려는 개발자
> **티켓**: [S14P31B101-90](https://ssafy.atlassian.net/browse/S14P31B101-90)
> **작성**: 2026-05-07

코드와 함께 보는 실용 가이드. 카피-페이스트로 시작할 수 있게 스니펫 위주.

---

## 1. API 스펙

### 1.1 엔드포인트

```
GET /api/v1/projects/{projectId}/reports/campaign-funnel
```

### 1.2 권한

OWNER 권한 필요 (`@projectPermission.hasRole(#projectId, 'OWNER')`).

### 1.3 쿼리 파라미터

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `from` | ISO date (YYYY-MM-DD) | ✅ | 조회 시작일 (포함) |
| `to` | ISO date | ✅ | 조회 종료일 (포함) |
| `campaignId` | long | ❌ | 단일 캠페인. 미지정 시 프로젝트 전체 합산 |
| `channel` | enum | ❌ | `PUSH` / `EMAIL` / `SMS` / `KAKAO_AT`. 미지정 시 모든 채널 합산 |

**제약**:
- `from > to` → `400 ANALYTICS_002 INVALID_DATE_RANGE`
- 기간 367일 초과 → `400 ANALYTICS_003 DATE_RANGE_TOO_LARGE`

### 1.4 응답 (200 OK)

```json
{
  "success": true,
  "data": {
    "projectId": 1,
    "campaignId": null,
    "channel": null,
    "from": "2026-04-01",
    "to": "2026-04-30",
    "stages": [
      {
        "stage": "SENT",
        "count": 1000,
        "conversionFromStart": 100.00,
        "conversionFromPrev": 100.00,
        "dropOffFromPrev": 0
      },
      {
        "stage": "DELIVERED",
        "count": 800,
        "conversionFromStart": 80.00,
        "conversionFromPrev": 80.00,
        "dropOffFromPrev": 20.00
      },
      {
        "stage": "OPEN",
        "count": 400,
        "conversionFromStart": 40.00,
        "conversionFromPrev": 50.00,
        "dropOffFromPrev": 50.00
      },
      {
        "stage": "CLICK",
        "count": 100,
        "conversionFromStart": 10.00,
        "conversionFromPrev": 25.00,
        "dropOffFromPrev": 75.00
      },
      {
        "stage": "CONVERT",
        "count": 25,
        "conversionFromStart": 2.50,
        "conversionFromPrev": 25.00,
        "dropOffFromPrev": 75.00
      }
    ],
    "biggestDropOffStage": "CLICK"
  }
}
```

### 1.5 응답 필드 의미

| 필드 | 타입 | 의미 |
|---|---|---|
| `stages[].stage` | enum | `SENT` / `DELIVERED` / `OPEN` / `CLICK` / `CONVERT` (펀널 순서) |
| `stages[].count` | long | 해당 단계 누적 카운트 |
| `stages[].conversionFromStart` | decimal\|null | SENT 대비 도달 비율 (%). 분모 0이면 null |
| `stages[].conversionFromPrev` | decimal\|null | 직전 단계 대비 도달 비율 (%). 첫 단계는 100, 분모 0이면 null |
| `stages[].dropOffFromPrev` | decimal\|null | 100 - conversionFromPrev. 첫 단계는 0, null이면 null |
| `biggestDropOffStage` | enum\|null | 직전 단계 대비 이탈률 최대 단계. 데이터 부족 시 null |

**stages 배열은 항상 5단계 모두 포함**한다. count 0인 단계도 빠지지 않으니 클라이언트가 빈 칸 처리할 필요 없음.

---

## 2. 호출 예시

### 2.1 cURL

```bash
# 프로젝트 1의 4월 한 달 전체 펀널
curl -H "Authorization: Bearer $TOKEN" \
  "https://api.butterfly.com/api/v1/projects/1/reports/campaign-funnel?from=2026-04-01&to=2026-04-30"

# 단일 캠페인 + PUSH 채널만
curl -H "Authorization: Bearer $TOKEN" \
  "https://api.butterfly.com/api/v1/projects/1/reports/campaign-funnel?from=2026-04-01&to=2026-04-30&campaignId=4521&channel=PUSH"
```

### 2.2 프론트 (TanStack Query + axios)

```ts
import { useQuery } from '@tanstack/react-query';
import { z } from 'zod';

const stageSchema = z.object({
  stage: z.enum(['SENT', 'DELIVERED', 'OPEN', 'CLICK', 'CONVERT']),
  count: z.number(),
  conversionFromStart: z.number().nullable(),
  conversionFromPrev: z.number().nullable(),
  dropOffFromPrev: z.number().nullable(),
});

const responseSchema = z.object({
  projectId: z.number(),
  campaignId: z.number().nullable(),
  channel: z.enum(['PUSH', 'EMAIL', 'SMS', 'KAKAO_AT']).nullable(),
  from: z.string(),
  to: z.string(),
  stages: z.array(stageSchema).length(5),
  biggestDropOffStage: stageSchema.shape.stage.nullable(),
});

export function useCampaignFunnel(params: {
  projectId: number;
  from: string;
  to: string;
  campaignId?: number;
  channel?: 'PUSH' | 'EMAIL' | 'SMS' | 'KAKAO_AT';
}) {
  return useQuery({
    queryKey: ['campaign-funnel', params],
    queryFn: async () => {
      const { data } = await axios.get(
        `/api/v1/projects/${params.projectId}/reports/campaign-funnel`,
        { params: { from: params.from, to: params.to,
                    campaignId: params.campaignId, channel: params.channel } }
      );
      return responseSchema.parse(data.data);
    },
  });
}
```

**주의**: `conversionFromStart`/`conversionFromPrev`/`dropOffFromPrev`는 **null 가능**. UI에서 그대로 `value.toFixed(2)`하면 NPE 난다. `null`이면 `—`로 표시.

```tsx
function StageRow({ stage }: { stage: FunnelStage }) {
  return (
    <div>
      <span>{stage.stage}: {stage.count.toLocaleString()}</span>
      <span>{stage.conversionFromPrev?.toFixed(2) ?? '—'}%</span>
    </div>
  );
}
```

---

## 3. 비슷한 펀널을 다른 도메인에 도입하기 — 체크리스트

본 PR 패턴을 다른 도메인(예: 세그먼트 빌드 단계 펀널, 이벤트 수집 단계 펀널)에 적용하려면:

### 3.1 데이터가 단계별 카운트로 이미 합산되어 있는가?

본 PR이 빠르게 만들 수 있었던 이유는 `campaign_stats_daily`에 이미 5단계 카운트가 컬럼으로 쌓여 있어서다. 단계별 합산 테이블이 없으면 다음을 먼저 결정한다:

- **Hot path (실시간)**: 매 요청마다 raw 로그를 GROUP BY 하면 비싸다. 백그라운드 합산 워커가 먼저 필요.
- **Cold path (배치/리포트)**: raw 로그를 그대로 GROUP BY 해도 OK. 운영 부하 낮음.

### 3.2 단계 정의가 시퀀셜한가?

펀널은 **순서가 의미를 가져야** 한다. "발송 → 전달 → 오픈"처럼. 만약 단계가 순서가 없으면 펀널이 아니라 매트릭스(92번 패턴)를 써야 한다.

### 3.3 비율 정의 (KPI 정의서 §3 준수)

```java
private static BigDecimal ratePercent(long numerator, long denominator) {
    if (denominator == 0) return null;  // 분모 0이면 null
    return BigDecimal.valueOf(numerator)
            .multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(denominator),
                    2, RoundingMode.HALF_UP);  // 소수점 둘째 자리 + 반올림
}
```

본 프로젝트 모든 비율 지표가 이 규칙. 새 도메인도 따라야 한다.

### 3.4 코드 골격 — Service 패턴

```java
@Service
@RequiredArgsConstructor
public class YourFunnelService {

    private static final int RATE_SCALE = 2;
    private static final long MAX_RANGE_DAYS = 366;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final YourStatsRepository repository;

    @Transactional(readOnly = true)
    public YourFunnelResponse getFunnel(YourFunnelQuery query) {
        validate(query);

        // 1. 단일 SUM 쿼리로 모든 단계 카운트 가져옴
        var counts = repository.aggregateFunnel(query.projectId(),
                query.from(), query.to(), /* filters */);
        if (counts == null) counts = YourCountRow.zero();

        // 2. 단계별 row 생성 — 시작/직전 대비 비율 계산
        List<StageRow> stages = buildStages(counts);

        // 3. 최대 이탈 단계 추출
        StageEnum biggest = findBiggestDropOff(stages);

        return new YourFunnelResponse(/* ... */ stages, biggest);
    }

    // validate, buildStages, findBiggestDropOff 는 본 PR 코드 복사
}
```

### 3.5 JPQL 패턴 — 단일 합산 쿼리

```java
@Query("""
        SELECT new com.b101.butterfly.your.dto.YourCountRow(
            COALESCE(SUM(CAST(c.stageOneCount AS long)), 0L),
            COALESCE(SUM(CAST(c.stageTwoCount AS long)), 0L),
            ...
        )
        FROM YourStats c
        WHERE c.projectId = :projectId
          AND c.bucketDate >= :from
          AND c.bucketDate <= :to
          AND (:filterX IS NULL OR c.x = :filterX)
        """)
YourCountRow aggregateFunnel(/* params */);
```

**`COALESCE(SUM(...), 0L)`이 중요**. 매칭 row가 0건이면 SUM이 NULL을 반환하고, record 생성자가 long을 받기 때문에 NPE가 난다. COALESCE로 0L fallback.

**`(:param IS NULL OR c.field = :param)` 패턴**: 옵션 필터를 단일 메서드에서 처리. JPQL이 `:param IS NULL`을 인식하므로 동적 쿼리 빌더 없이 깔끔.

### 3.6 테스트 패턴

#### 단위 테스트 (Service)
- Mockito로 repository mock
- 정상 케이스 + 비율 0건 + 분모 0 + 필터 반영 + biggestDropOff 동률 처리
- 본 PR `CampaignFunnelServiceTest` 참고

#### 통합 테스트 (Repository)
- Testcontainers MySQL + JdbcTemplate으로 직접 INSERT
- **고유 projectId로 데이터 격리** (testcontainer 정적 공유 + native UPSERT 트랜잭션 한계 방어)
- BeforeEach + AfterEach 둘 다 cleanup
- COALESCE 동작 검증을 위해 "매칭 0건" 케이스 필수

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class YourFunnelQueryIntegrationTest extends AbstractMySqlContainerTest {

    @Autowired private YourStatsRepository repository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private static final long PROJECT_ID = 99_001L;  // 본 테스트 전용

    @BeforeEach @AfterEach
    void cleanIsolatedRange() {
        jdbcTemplate.update("DELETE FROM your_stats WHERE project_id = ?", PROJECT_ID);
    }
}
```

---

## 4. 자주 묻는 질문

### Q1. "biggestDropOff가 SENT다"가 가능한가?

불가능. SENT는 첫 단계라 dropOff = 100 - 100 = 0이고, 본 구현은 첫 단계를 후보에서 제외. 본 필드는 항상 `DELIVERED` / `OPEN` / `CLICK` / `CONVERT` 또는 `null`.

### Q2. CONVERT 카운트가 sent보다 클 수 있나?

이론상 안 된다 — convert는 click의 부분집합이고 click ⊂ open ⊂ delivered ⊂ sent. 하지만 데이터 정합성 깨짐(예: user_action_logs 적재 시점이 발송보다 먼저 들어옴)이 있으면 가능. 그런 경우 conversionFromPrev > 100% 가 나올 수 있고, 그건 데이터 이슈 신호로 받아들여 정합성 검증(126/187)에서 잡혀야 한다.

### Q3. `stages` 배열의 순서가 보장되나?

YES. 항상 `[SENT, DELIVERED, OPEN, CLICK, CONVERT]` 순서로 들어간다. UI에서 인덱스로 접근해도 안전.

### Q4. CSV로 다운로드받고 싶다.

본 PR에는 없다. 91번(공통 CSV 컴포넌트)이 들어오면 같이 통합 예정. 임시로는 프론트에서 stages를 CSV로 내보내는 클라이언트 사이드 export 권장.

### Q5. 다른 캠페인 채널 enum (notification 모듈의 Channel)이 있는데 이름이 같다. 어떤 걸 import?

본 API는 **`com.b101.butterfly.reporting.notification.entity.Channel`**을 사용 (reporting 모듈 내부 enum). campaign 도메인의 Channel과는 동일 이름이지만 다른 클래스. 어댑터 레벨에서 변환된다.

### Q6. 자동화 플로우 노드별 펀널은 언제 추가되나?

S14P31B101-30(자동화 플로우 도메인 생성) 머지 후 별도 티켓에서. 본 캠페인 펀널 코드는 그대로 두고 새 endpoint(`/api/v1/projects/{id}/reports/flow-funnel/{flowId}`)가 추가될 예정.

---

## 5. 관련 코드 빠른 참조

| 파일 | 역할 |
|---|---|
| [CampaignFunnelController.java](../src/main/java/com/b101/butterfly/reporting/report/controller/CampaignFunnelController.java) | HTTP 엔드포인트 |
| [CampaignFunnelService.java](../src/main/java/com/b101/butterfly/reporting/report/service/CampaignFunnelService.java) | 비즈니스 로직 (validate, buildStages, findBiggestDropOff) |
| [CampaignStatsDailyRepository.aggregateFunnel](../src/main/java/com/b101/butterfly/reporting/aggregation/repository/CampaignStatsDailyRepository.java) | 단일 SUM 쿼리 |
| [CampaignFunnelQuery](../src/main/java/com/b101/butterfly/reporting/report/dto/CampaignFunnelQuery.java) | 입력 DTO |
| [CampaignFunnelResponse](../src/main/java/com/b101/butterfly/reporting/report/dto/CampaignFunnelResponse.java) | 응답 DTO |
| [FunnelCountRow](../src/main/java/com/b101/butterfly/reporting/report/dto/FunnelCountRow.java) | JPQL 결과 매핑 record |
| [FunnelStage](../src/main/java/com/b101/butterfly/reporting/report/dto/FunnelStage.java) | 단계 enum |
| [CampaignFunnelStageRow](../src/main/java/com/b101/butterfly/reporting/report/dto/CampaignFunnelStageRow.java) | 응답의 단일 단계 row |
| [CampaignFunnelServiceTest](../src/test/java/com/b101/butterfly/reporting/report/service/CampaignFunnelServiceTest.java) | Service 단위 테스트 |
| [CampaignStatsDailyFunnelQueryIntegrationTest](../src/test/java/com/b101/butterfly/reporting/aggregation/repository/CampaignStatsDailyFunnelQueryIntegrationTest.java) | JPQL 통합 테스트 |
