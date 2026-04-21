# Analytics 모듈 API (역할6: 분석/대시보드)

담당 테이블: `campaign_stats`

---

## 분석 API (Management API, JWT 인증)

| # | Method | URL | 설명 | 최소 권한 | 비고 |
|---|--------|-----|------|----------|------|
| 57 | GET | `/api/projects/{projectId}/analytics/dashboard` | 대시보드 요약 | VIEWER | 집계 테이블 조회 |
| 58 | GET | `/api/projects/{projectId}/campaigns/{campaignId}/stats` | 캠페인별 통계 | VIEWER | campaign_stats 1:1 |
| 59 | GET | `/api/projects/{projectId}/analytics/campaigns` | 캠페인 성과 비교 | VIEWER | 기간 필터 |
| 60 | GET | `/api/projects/{projectId}/analytics/channels` | 채널별 성과 비교 | VIEWER | PUSH vs EMAIL |
| 61 | GET | `/api/projects/{projectId}/analytics/segments` | 세그먼트별 성과 | VIEWER | |

---

### GET /api/projects/{projectId}/campaigns/{campaignId}/stats

```json
// Response
{
  "campaignId": 1,
  "totalTarget": 2340,
  "sentCount": 2300,
  "deliveredCount": 2150,
  "clickedCount": 430,
  "failedCount": 40,
  "deliveryRate": 93.47,
  "clickRate": 20.0,
  "updatedAt": "2026-04-20T09:30:00"
}
```

---

### GET /api/projects/{projectId}/analytics/dashboard

```
?from=2026-04-01&to=2026-04-20
```

```json
// Response
{
  "period": { "from": "2026-04-01", "to": "2026-04-20" },
  "summary": {
    "totalCampaigns": 15,
    "totalSent": 45000,
    "totalDelivered": 42000,
    "totalClicked": 8500,
    "totalFailed": 1200,
    "avgDeliveryRate": 93.3,
    "avgClickRate": 20.2
  },
  "dailyTrend": [
    { "date": "2026-04-01", "sent": 3000, "delivered": 2800, "clicked": 560 },
    { "date": "2026-04-02", "sent": 2500, "delivered": 2350, "clicked": 470 }
  ]
}
```

---

### GET /api/projects/{projectId}/analytics/campaigns

```
?from=2026-04-01&to=2026-04-20&page=0&size=10
```

```json
// Response
{
  "content": [
    {
      "campaignId": 1,
      "campaignName": "봄 세일 알림",
      "channel": "PUSH",
      "sentCount": 2300,
      "deliveredCount": 2150,
      "clickedCount": 430,
      "failedCount": 40,
      "deliveryRate": 93.47,
      "clickRate": 20.0
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 15
}
```

---

### GET /api/projects/{projectId}/analytics/channels

```
?from=2026-04-01&to=2026-04-20
```

```json
// Response
{
  "channels": [
    {
      "channel": "PUSH",
      "sentCount": 40000,
      "deliveredCount": 38000,
      "clickedCount": 7600,
      "failedCount": 900,
      "deliveryRate": 95.0,
      "clickRate": 20.0
    },
    {
      "channel": "EMAIL",
      "sentCount": 5000,
      "deliveredCount": 4000,
      "clickedCount": 900,
      "failedCount": 300,
      "deliveryRate": 80.0,
      "clickRate": 22.5
    }
  ]
}
```

---

### GET /api/projects/{projectId}/analytics/segments

```
?from=2026-04-01&to=2026-04-20
```

```json
// Response
{
  "segments": [
    {
      "segmentId": 1,
      "segmentName": "서울 VIP 유저",
      "campaignCount": 5,
      "sentCount": 12000,
      "deliveredCount": 11200,
      "clickedCount": 2800,
      "deliveryRate": 93.3,
      "clickRate": 25.0
    },
    {
      "segmentId": 2,
      "segmentName": "신규 가입 유저",
      "campaignCount": 3,
      "sentCount": 8000,
      "deliveredCount": 7500,
      "clickedCount": 1200,
      "deliveryRate": 93.7,
      "clickRate": 16.0
    }
  ]
}
```
