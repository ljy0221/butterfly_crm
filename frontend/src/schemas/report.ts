import { z } from "zod";

/**
 * Sprint S14P31B101-92 — Reporting (Segment × Channel Matrix) Zod 스키마.
 *
 * 백엔드 패키지: com.b101.butterfly.reporting.report.dto
 *  - SegmentChannelMatrixResponse (rows + highlights)
 *  - SegmentChannelMatrixRow      (BigDecimal nullable rate 4종)
 *  - SegmentChannelMatrixQuery.SortBy
 *
 * 비율 지표(deliveryRate/openRate/clickRate/convertRate)는 분모가 0이면 null —
 * KPI 정의서 §3 (분모 0 ⇒ 비율 정의 불가). 화면에서는 formatRate 유틸로 "—" 표기.
 */

/** notification_logs.channel ENUM (V1 마이그레이션). */
export const ChannelSchema = z.enum(["PUSH", "EMAIL", "SMS", "WEBHOOK", "KAKAO"]);
export type Channel = z.infer<typeof ChannelSchema>;

/** SegmentChannelMatrixQuery.SortBy enum. */
export const SegmentChannelMatrixSortBySchema = z.enum([
  "SENT_COUNT",
  "DELIVERED_COUNT",
  "OPEN_RATE",
  "CLICK_RATE",
  "CONVERT_RATE",
]);
export type SegmentChannelMatrixSortBy = z.infer<
  typeof SegmentChannelMatrixSortBySchema
>;

/**
 * 백엔드 BigDecimal 직렬화는 Jackson 기본값상 number 또는 string 모두 가능 —
 * 양쪽을 모두 받아 number | null로 정규화한다 (ADR-0007 nullable rate 정책).
 */
const rateSchema = z
  .union([z.number(), z.string(), z.null()])
  .transform((v): number | null => {
    if (v === null) return null;
    if (typeof v === "number") return Number.isFinite(v) ? v : null;
    const parsed = Number(v);
    return Number.isFinite(parsed) ? parsed : null;
  });

export const SegmentChannelMatrixRowSchema = z.object({
  segmentId: z.number().nullable(),
  segmentName: z.string().nullable(),
  channel: ChannelSchema,
  sentCount: z.number(),
  deliveredCount: z.number(),
  failedCount: z.number(),
  openCount: z.number(),
  clickCount: z.number(),
  convertCount: z.number(),
  deliveryRate: rateSchema,
  openRate: rateSchema,
  clickRate: rateSchema,
  convertRate: rateSchema,
});
export type SegmentChannelMatrixRow = z.infer<
  typeof SegmentChannelMatrixRowSchema
>;

export const SegmentChannelMatrixHighlightsSchema = z.object({
  topByOpenRate: SegmentChannelMatrixRowSchema.nullable(),
  topByClickRate: SegmentChannelMatrixRowSchema.nullable(),
  topByConvertRate: SegmentChannelMatrixRowSchema.nullable(),
  bottomByOpenRate: SegmentChannelMatrixRowSchema.nullable(),
});
export type SegmentChannelMatrixHighlights = z.infer<
  typeof SegmentChannelMatrixHighlightsSchema
>;

export const SegmentChannelMatrixResponseSchema = z.object({
  projectId: z.number(),
  from: z.string(),
  to: z.string(),
  rows: z.array(SegmentChannelMatrixRowSchema),
  highlights: SegmentChannelMatrixHighlightsSchema,
});
export type SegmentChannelMatrixResponse = z.infer<
  typeof SegmentChannelMatrixResponseSchema
>;
