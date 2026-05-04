import { api } from "./client";
import { ifMock } from "./_mock";
import { getCampaignStatsMock } from "./_mock/campaign";

/**
 * Sprint 3 — Analytics(대시보드/메트릭) API.
 *
 * 백엔드 패키지: com.b101.butterfly.reporting
 *  - controller/AnalyticsController.java
 *  - dto/{DashboardResponse, CampaignMetricItem, ChannelMetricsResponse,
 *        ChannelMetricItem, CampaignStatsResponse, ExecutionStatsResponse,
 *        SegmentMetricsResponse, SegmentMetricItem}.java
 *
 * 응답은 client.ts 인터셉터가 ApiResponse<T> 래퍼를 자동 unwrap 하므로
 * 호출 측은 `res.data`로 곧바로 도메인 페이로드(T)를 받는다.
 *
 * 모든 엔드포인트는 클래스 레벨 @PreAuthorize("VIEWER 이상") 적용.
 */

// ---------- 공통 타입 ----------

/**
 * 백엔드 PageResponse<T> (com.b101.butterfly.global.response.PageResponse).
 *
 * 주의: tenant.application의 SpringPage<T>는 Spring Data PageImpl 직렬화 결과
 * (`number` 필드)이지만, butterfly-reporting의 PageResponse는 record로 직접 정의된
 * `page` 필드를 사용한다.
 */
export interface SpringPage<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

// ---------- DTO 타입 (백엔드 record 필드명 그대로) ----------

/** API 57: GET /api/projects/{projectId}/analytics/dashboard */
export interface DashboardResponse {
  period: { from: string; to: string };
  summary: DashboardSummary;
  dailyTrend: DailyPoint[];
  /** LocalDateTime — 집계 기준 시각 */
  metricsComputedAt: string;
}

export interface DashboardSummary {
  campaignsSentInPeriod: number;
  totalSent: number;
  totalDelivered: number;
  totalClicked: number;
  totalFailed: number;
  /** 0.0 ~ 1.0 (소수 비율) */
  avgDeliveryRate: number;
  /** 0.0 ~ 1.0 (소수 비율) */
  avgClickRate: number;
}

export interface DailyPoint {
  /** yyyy-MM-dd */
  date: string;
  sent: number;
  delivered: number;
  clicked: number;
}

/** API 59 응답의 content 항목 */
export interface CampaignMetricItem {
  campaignId: number;
  campaignName: string;
  /** PUSH, EMAIL ... */
  channel: string;
  sentCount: number;
  deliveredCount: number;
  clickedCount: number;
  failedCount: number;
  deliveryRate: number;
  clickRate: number;
}

/** API 60: GET /api/projects/{projectId}/analytics/channels */
export interface ChannelMetricsResponse {
  channels: ChannelMetricItem[];
}

export interface ChannelMetricItem {
  channel: string;
  sentCount: number;
  deliveredCount: number;
  clickedCount: number;
  failedCount: number;
  deliveryRate: number;
  clickRate: number;
}

/** API 58: GET /api/projects/{projectId}/campaigns/{campaignId}/stats */
export interface CampaignStatsResponse {
  campaignId: number;
  totalTarget: number;
  sentCount: number;
  deliveredCount: number;
  clickedCount: number;
  failedCount: number;
  deliveryRate: number;
  clickRate: number;
  executions: ExecutionSummary[];
  metricsComputedAt: string;
}

export interface ExecutionSummary {
  executionId: number;
  /** SCHEDULED | RUNNING | COMPLETED | FAILED ... */
  status: string;
  sendStartedAt: string;
  sentCount: number;
  deliveredCount: number;
  clickedCount: number;
}

/**
 * 신규 API: GET /api/projects/{projectId}/campaigns/{campaignId}/executions/{executionId}/stats
 * 회차별 상세 통계 (ADR-0002, ADR-0003).
 * API 58은 회차 누적 합산을, 본 응답은 단일 회차의 상세 집계를 반환.
 */
export interface ExecutionStatsResponse {
  campaignId: number;
  executionId: number;
  /** SCHEDULED | SENDING | COMPLETED | FAILED | CANCELED */
  status: string;
  /** SCHEDULED 상태에서는 0 (아직 세그먼트 미계산) */
  totalTarget: number;
  sentCount: number;
  deliveredCount: number;
  clickedCount: number;
  failedCount: number;
  /** 0.0 ~ 1.0 */
  deliveryRate: number;
  /** 0.0 ~ 1.0 (분모 = deliveredCount) */
  clickRate: number;
  sendStartedAt: string | null;
  sendCompletedAt: string | null;
  metricsComputedAt: string;
}

/** API 61: GET /api/projects/{projectId}/analytics/segments */
export interface SegmentMetricsResponse {
  segments: SegmentMetricItem[];
}

export interface SegmentMetricItem {
  segmentId: number;
  segmentName: string;
  campaignCount: number;
  sentCount: number;
  deliveredCount: number;
  clickedCount: number;
  /** 0.0 ~ 1.0 */
  deliveryRate: number;
  /** 0.0 ~ 1.0 */
  clickRate: number;
}

// ---------- 요청 파라미터 ----------

export interface DashboardParams {
  /** yyyy-MM-dd */
  from: string;
  /** yyyy-MM-dd */
  to: string;
}

export interface CampaignMetricsParams extends DashboardParams {
  /** 0-indexed. 기본 0 */
  page?: number;
  /** 기본 10 */
  size?: number;
  /** PUSH | EMAIL ... */
  channel?: string;
  segmentId?: number;
}

// ---------- 엔드포인트 ----------

/** API 57. VIEWER 이상. */
export async function getDashboard(
  projectId: number,
  params: DashboardParams,
): Promise<DashboardResponse> {
  const res = await api.get<DashboardResponse>(
    `/api/projects/${projectId}/analytics/dashboard`,
    { params },
  );
  return res.data;
}

/** API 59. VIEWER 이상. */
export async function getCampaignMetrics(
  projectId: number,
  params: CampaignMetricsParams,
): Promise<SpringPage<CampaignMetricItem>> {
  const res = await api.get<SpringPage<CampaignMetricItem>>(
    `/api/projects/${projectId}/analytics/campaigns`,
    {
      params: {
        from: params.from,
        to: params.to,
        page: params.page ?? 0,
        size: params.size ?? 10,
        channel: params.channel,
        segmentId: params.segmentId,
      },
    },
  );
  return res.data;
}

/** API 60. VIEWER 이상. */
export async function getChannelMetrics(
  projectId: number,
  params: DashboardParams,
): Promise<ChannelMetricsResponse> {
  const res = await api.get<ChannelMetricsResponse>(
    `/api/projects/${projectId}/analytics/channels`,
    { params },
  );
  return res.data;
}

/**
 * API 58. Sprint 6 캠페인 상세 화면용.
 * 클릭률 분모는 deliveredCount (ADR-0003).
 *
 * Sprint 6 시점 — campaign 도메인이 mock-fallback 이라 실제 백엔드가 캠페인 통계를
 * 제공할 수 없다. 따라서 campaign 플래그가 켜져 있을 때(=캠페인 mock 사용 중)는
 * mock store 의 sendCount/openRate 기반 더미 stats 를 반환한다.
 */
export async function getCampaignStats(
  projectId: number,
  campaignId: number,
): Promise<CampaignStatsResponse> {
  return ifMock<CampaignStatsResponse>(
    "campaign",
    async () => getCampaignStatsMock(campaignId),
    async () => {
      const res = await api.get<CampaignStatsResponse>(
        `/api/projects/${projectId}/campaigns/${campaignId}/stats`,
      );
      return res.data;
    },
  );
}

/**
 * 신규 API. 회차별 상세 통계.
 * GET /api/projects/{projectId}/campaigns/{campaignId}/executions/{executionId}/stats
 *
 * campaign 도메인이 mock-fallback 일 때는 백엔드 호출이 의미 없으므로
 * 빈 stats 더미를 반환한다(executionId 가 mock 캠페인의 회차이므로).
 */
export async function getExecutionStats(
  projectId: number,
  campaignId: number,
  executionId: number,
): Promise<ExecutionStatsResponse> {
  return ifMock<ExecutionStatsResponse>(
    "campaign",
    async () => ({
      campaignId,
      executionId,
      status: "COMPLETED",
      totalTarget: 0,
      sentCount: 0,
      deliveredCount: 0,
      clickedCount: 0,
      failedCount: 0,
      deliveryRate: 0,
      clickRate: 0,
      sendStartedAt: null,
      sendCompletedAt: null,
      metricsComputedAt: new Date().toISOString(),
    }),
    async () => {
      const res = await api.get<ExecutionStatsResponse>(
        `/api/projects/${projectId}/campaigns/${campaignId}/executions/${executionId}/stats`,
      );
      return res.data;
    },
  );
}

/**
 * API 61. 세그먼트별 메트릭. VIEWER 이상.
 * GET /api/projects/{projectId}/analytics/segments
 */
export async function getSegmentMetrics(
  projectId: number,
  params: DashboardParams,
): Promise<SegmentMetricsResponse> {
  const res = await api.get<SegmentMetricsResponse>(
    `/api/projects/${projectId}/analytics/segments`,
    { params },
  );
  return res.data;
}
