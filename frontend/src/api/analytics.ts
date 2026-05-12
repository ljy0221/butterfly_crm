import { api } from "./client";
import { ifMock } from "./_mock";
import { getCampaignStatsMock } from "./_mock/campaign";
import {
  CampaignMetricsPageSchema,
  CampaignStatsResponseSchema,
  ChannelMetricsResponseSchema,
  DashboardResponseSchema,
  ExecutionStatsResponseSchema,
  SegmentMetricsResponseSchema,
  type CampaignMetricItem,
  type CampaignStatsResponse,
  type ChannelMetricsResponse,
  type DashboardResponse,
  type ExecutionStatsResponse,
  type SegmentMetricsResponse,
} from "@/schemas/analytics";

/**
 * Sprint 3 — Analytics(대시보드/메트릭) API.
 *
 * 백엔드 패키지: com.b101.butterfly.reporting
 *  - controller/AnalyticsController.java
 *  - dto/{DashboardResponse, CampaignMetricItem, ChannelMetricsResponse,
 *        ChannelMetricItem, CampaignStatsResponse, ExecutionStatsResponse,
 *        SegmentMetricsResponse, SegmentMetricItem}.java
 *
 * 응답은 client.ts 인터셉터가 ApiResponse<T> 래퍼를 자동 unwrap 하고,
 * 본 모듈에서 한 번 더 zod parse를 통과시켜 nullable rate(분모 0 ⇒ null) 정책을 강제한다.
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

// ---------- DTO 타입 — schemas/analytics.ts에서 zod 추론 ----------

export type {
  CampaignMetricItem,
  CampaignMetricsPage,
  CampaignStatsResponse,
  ChannelMetricItem,
  ChannelMetricsResponse,
  DailyPoint,
  DashboardResponse,
  DashboardSummary,
  ExecutionStatsResponse,
  ExecutionSummary,
  SegmentMetricItem,
  SegmentMetricsResponse,
} from "@/schemas/analytics";

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
  const res = await api.get(
    `/api/projects/${projectId}/analytics/dashboard`,
    { params },
  );
  return DashboardResponseSchema.parse(res.data);
}

/** API 59. VIEWER 이상. */
export async function getCampaignMetrics(
  projectId: number,
  params: CampaignMetricsParams,
): Promise<SpringPage<CampaignMetricItem>> {
  const res = await api.get(
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
  return CampaignMetricsPageSchema.parse(res.data);
}

/** API 60. VIEWER 이상. */
export async function getChannelMetrics(
  projectId: number,
  params: DashboardParams,
): Promise<ChannelMetricsResponse> {
  const res = await api.get(
    `/api/projects/${projectId}/analytics/channels`,
    { params },
  );
  return ChannelMetricsResponseSchema.parse(res.data);
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
      const res = await api.get(
        `/api/projects/${projectId}/campaigns/${campaignId}/stats`,
      );
      return CampaignStatsResponseSchema.parse(res.data);
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
      openCount: 0,
      clickedCount: 0,
      convertCount: 0,
      failedCount: 0,
      // 분모 0이라 비율 정의 불가 — KPI 정의서 §3.
      deliveryRate: null,
      openRate: null,
      clickRate: null,
      convertRate: null,
      sendStartedAt: null,
      sendCompletedAt: null,
      metricsComputedAt: new Date().toISOString(),
    }),
    async () => {
      const res = await api.get(
        `/api/projects/${projectId}/campaigns/${campaignId}/executions/${executionId}/stats`,
      );
      return ExecutionStatsResponseSchema.parse(res.data);
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
  const res = await api.get(
    `/api/projects/${projectId}/analytics/segments`,
    { params },
  );
  return SegmentMetricsResponseSchema.parse(res.data);
}
