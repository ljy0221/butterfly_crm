import { z } from "zod";
import { ChannelSchema } from "./report";

/**
 * Sprint 3 — Analytics(대시보드/메트릭) Zod 스키마.
 *
 * 백엔드 패키지: com.b101.butterfly.reporting.dto
 *  - DashboardResponse, CampaignMetricItem, ChannelMetricsResponse,
 *    ChannelMetricItem, SegmentMetricsResponse, SegmentMetricItem
 *
 * 비율 지표(deliveryRate/clickRate)는 백엔드 v1 record에서 `double` non-null로 직렬화되지만,
 * ADR-0007 supersede 후 nullable로 전환될 수 있으므로 미리 nullable로 받아 둔다.
 * 분모 0이면 null — formatRate 유틸이 "—"로 표기.
 */

const rateSchema = z
  .union([z.number(), z.string(), z.null()])
  .transform((v): number | null => {
    if (v === null) return null;
    if (typeof v === "number") return Number.isFinite(v) ? v : null;
    const parsed = Number(v);
    return Number.isFinite(parsed) ? parsed : null;
  });

export const DailyPointSchema = z.object({
  date: z.string(),
  sent: z.number(),
  delivered: z.number(),
  clicked: z.number(),
});
export type DailyPoint = z.infer<typeof DailyPointSchema>;

export const DashboardSummarySchema = z.object({
  campaignsSentInPeriod: z.number(),
  totalSent: z.number(),
  totalDelivered: z.number(),
  totalClicked: z.number(),
  totalFailed: z.number(),
  avgDeliveryRate: rateSchema,
  avgClickRate: rateSchema,
});
export type DashboardSummary = z.infer<typeof DashboardSummarySchema>;

export const DashboardResponseSchema = z.object({
  period: z.object({ from: z.string(), to: z.string() }),
  summary: DashboardSummarySchema,
  dailyTrend: z.array(DailyPointSchema),
  metricsComputedAt: z.string(),
});
export type DashboardResponse = z.infer<typeof DashboardResponseSchema>;

export const CampaignMetricItemSchema = z.object({
  campaignId: z.number(),
  campaignName: z.string(),
  channel: z.string(),
  sentCount: z.number(),
  deliveredCount: z.number(),
  clickedCount: z.number(),
  failedCount: z.number(),
  deliveryRate: rateSchema,
  clickRate: rateSchema,
});
export type CampaignMetricItem = z.infer<typeof CampaignMetricItemSchema>;

export const ChannelMetricItemSchema = z.object({
  channel: z.string(),
  sentCount: z.number(),
  deliveredCount: z.number(),
  clickedCount: z.number(),
  failedCount: z.number(),
  deliveryRate: rateSchema,
  clickRate: rateSchema,
});
export type ChannelMetricItem = z.infer<typeof ChannelMetricItemSchema>;

export const ChannelMetricsResponseSchema = z.object({
  channels: z.array(ChannelMetricItemSchema),
});
export type ChannelMetricsResponse = z.infer<typeof ChannelMetricsResponseSchema>;

export const SegmentMetricItemSchema = z.object({
  segmentId: z.number(),
  segmentName: z.string(),
  campaignCount: z.number(),
  sentCount: z.number(),
  deliveredCount: z.number(),
  clickedCount: z.number(),
  deliveryRate: rateSchema,
  clickRate: rateSchema,
});
export type SegmentMetricItem = z.infer<typeof SegmentMetricItemSchema>;

export const SegmentMetricsResponseSchema = z.object({
  segments: z.array(SegmentMetricItemSchema),
});
export type SegmentMetricsResponse = z.infer<typeof SegmentMetricsResponseSchema>;

/** API 59 페이지 응답: PageResponse<CampaignMetricItem>. */
export const CampaignMetricsPageSchema = z.object({
  content: z.array(CampaignMetricItemSchema),
  page: z.number(),
  size: z.number(),
  totalElements: z.number(),
  totalPages: z.number(),
  first: z.boolean().optional(),
  last: z.boolean().optional(),
  empty: z.boolean().optional(),
});
export type CampaignMetricsPage = z.infer<typeof CampaignMetricsPageSchema>;

/** report.ts ChannelSchema 재노출 — analytics 도메인에서도 사용. */
export { ChannelSchema };
export type { Channel } from "./report";
