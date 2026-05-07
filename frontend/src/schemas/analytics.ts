import { z } from "zod";
import { ChannelSchema } from "./report";

/**
 * Sprint 3 — Analytics(대시보드/메트릭) Zod 스키마.
 *
 * 백엔드 패키지: com.b101.butterfly.reporting.dto
 *  - DashboardResponse, CampaignMetricItem, ChannelMetricsResponse,
 *    ChannelMetricItem, SegmentMetricsResponse, SegmentMetricItem
 *
 * ADR-0007 supersede 적용 (2026-05-07):
 *  - OPEN / CONVERT 카운트 + 비율 4종 추가
 *  - 모든 비율 필드 nullable (분모 0 ⇒ null) — KPI 정의서 §3
 *  - clickRate 분모 의미: ADR-0003 clicked/delivered → ADR-0007 click/open
 *
 * 화면에서는 formatRate(value: number | null) 유틸로 "—" 표기.
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
  opened: z.number(),
  clicked: z.number(),
  converted: z.number(),
});
export type DailyPoint = z.infer<typeof DailyPointSchema>;

export const DashboardSummarySchema = z.object({
  campaignsSentInPeriod: z.number(),
  totalSent: z.number(),
  totalDelivered: z.number(),
  totalOpen: z.number(),
  totalClicked: z.number(),
  totalConvert: z.number(),
  totalFailed: z.number(),
  avgDeliveryRate: rateSchema,
  avgOpenRate: rateSchema,
  avgClickRate: rateSchema,
  avgConvertRate: rateSchema,
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
  openCount: z.number(),
  clickedCount: z.number(),
  convertCount: z.number(),
  failedCount: z.number(),
  deliveryRate: rateSchema,
  openRate: rateSchema,
  clickRate: rateSchema,
  convertRate: rateSchema,
});
export type CampaignMetricItem = z.infer<typeof CampaignMetricItemSchema>;

export const ChannelMetricItemSchema = z.object({
  channel: z.string(),
  sentCount: z.number(),
  deliveredCount: z.number(),
  openCount: z.number(),
  clickedCount: z.number(),
  convertCount: z.number(),
  failedCount: z.number(),
  deliveryRate: rateSchema,
  openRate: rateSchema,
  clickRate: rateSchema,
  convertRate: rateSchema,
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
  openCount: z.number(),
  clickedCount: z.number(),
  convertCount: z.number(),
  deliveryRate: rateSchema,
  openRate: rateSchema,
  clickRate: rateSchema,
  convertRate: rateSchema,
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
