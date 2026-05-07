import { z } from "zod";
import { ChannelSchema } from "./report";

/**
 * S14P31B101-90 — 캠페인 단계 펀널 Zod 스키마.
 *
 * 백엔드 패키지: com.b101.butterfly.reporting.report.dto
 *  - CampaignFunnelResponse, CampaignFunnelStageRow, FunnelStage
 *
 * 비율 3종(conversionFromStart/conversionFromPrev/dropOffFromPrev)은 분모 0이면 null —
 * KPI 정의서 §3. 화면에서는 formatRate 유틸로 "—" 표기.
 */

export const FunnelStageSchema = z.enum([
  "SENT",
  "DELIVERED",
  "OPEN",
  "CLICK",
  "CONVERT",
]);
export type FunnelStage = z.infer<typeof FunnelStageSchema>;

const rateSchema = z
  .union([z.number(), z.string(), z.null()])
  .transform((v): number | null => {
    if (v === null) return null;
    if (typeof v === "number") return Number.isFinite(v) ? v : null;
    const parsed = Number(v);
    return Number.isFinite(parsed) ? parsed : null;
  });

export const CampaignFunnelStageRowSchema = z.object({
  stage: FunnelStageSchema,
  count: z.number(),
  conversionFromStart: rateSchema,
  conversionFromPrev: rateSchema,
  dropOffFromPrev: rateSchema,
});
export type CampaignFunnelStageRow = z.infer<typeof CampaignFunnelStageRowSchema>;

export const CampaignFunnelResponseSchema = z.object({
  projectId: z.number(),
  campaignId: z.number().nullable(),
  channel: ChannelSchema.nullable(),
  from: z.string(),
  to: z.string(),
  stages: z.array(CampaignFunnelStageRowSchema),
  biggestDropOffStage: FunnelStageSchema.nullable(),
});
export type CampaignFunnelResponse = z.infer<typeof CampaignFunnelResponseSchema>;
