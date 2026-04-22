# butterfly-analytics

## 역할

캠페인 성과 분석 및 대시보드 데이터를 제공하는 모듈. 집계 테이블 기반으로 통계를 조회한다 (ADR-0003).

## 주요 기능

- **대시보드**: 기간별 전체 캠페인 요약 통계
- **캠페인별 통계**: 개별 캠페인의 발송/도달/클릭/실패 집계 (회차 누적 합산 — ADR-0002)
- **회차별 상세 통계**: 캠페인 실행(execution) 단위의 상세 통계
- **캠페인 성과 비교**: 기간/채널/세그먼트 필터 기반 캠페인 간 성과 비교
- **채널별 성과**: 채널(PUSH, EMAIL 등)별 성과 비교
- **세그먼트별 성과**: 세그먼트별 캠페인 성과 분석

## 담당 테이블

`campaign_stats`

## API 경로

- `/api/projects/{projectId}/analytics/**` — 분석/대시보드 (JWT 인증, VIEWER 이상)
- `/api/projects/{projectId}/campaigns/{campaignId}/stats` — 캠페인별 통계
- `/api/projects/{projectId}/campaigns/{campaignId}/executions/{executionId}/stats` — 회차별 통계

## 의존 관계

- `butterfly-common`, `butterfly-auth`, `butterfly-campaign`에 의존
- Spring Data JPA, Spring Security, MySQL
