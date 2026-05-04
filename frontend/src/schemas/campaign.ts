import { z } from "zod";

/**
 * Sprint 6 — Campaign Zod 스키마.
 *
 * 백엔드 도메인 (com.b101.butterfly.campaign.campaign.domain.*) 과 동일 enum.
 *  - CampaignStatus: DRAFT | ACTIVE | PAUSED | ENDED
 *  - CampaignType:   ONE_TIME | AUTOMATION
 *  - Channel:        PUSH | EMAIL | SMS | WEBHOOK | KAKAO
 *  - SendMode:       IMMEDIATE | SCHEDULED | RECURRING
 *  - RepeatType:     DAILY | WEEKLY | MONTHLY
 *  - ExecutionStatus: READY | SEND | SUCCESS | FAIL
 *
 * 검증 규칙은 Campaign 도메인 factory + RecurringCronGenerator + CampaignErrorCode (CAMPAIGN_001~014)
 * 를 그대로 옮겼다. 백엔드 미구현 단계에서 mock store/Wizard 가 동일 룰로 검증.
 */

// ---------- 도메인 enum ----------

export const ChannelSchema = z.enum(["PUSH", "EMAIL", "SMS", "WEBHOOK", "KAKAO"]);
export type Channel = z.infer<typeof ChannelSchema>;

export const CampaignTypeSchema = z.enum(["ONE_TIME", "AUTOMATION"]);
export type CampaignType = z.infer<typeof CampaignTypeSchema>;

export const CampaignStatusSchema = z.enum(["DRAFT", "ACTIVE", "PAUSED", "ENDED"]);
export type CampaignStatus = z.infer<typeof CampaignStatusSchema>;

export const SendModeSchema = z.enum(["IMMEDIATE", "SCHEDULED", "RECURRING"]);
export type SendMode = z.infer<typeof SendModeSchema>;

export const RepeatTypeSchema = z.enum(["DAILY", "WEEKLY", "MONTHLY"]);
export type RepeatType = z.infer<typeof RepeatTypeSchema>;

export const ExecutionStatusSchema = z.enum(["READY", "SEND", "SUCCESS", "FAIL"]);
export type ExecutionStatus = z.infer<typeof ExecutionStatusSchema>;

export const DAY_OF_WEEK_LIST = ["MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"] as const;
export type DayOfWeek = (typeof DAY_OF_WEEK_LIST)[number];

// ---------- 필드 단위 ----------

export const nameSchema = z
  .string()
  .min(1, "캠페인 이름은 필수입니다")
  .max(50, "캠페인 이름은 50자 이내여야 합니다");

export const descSchema = z
  .string()
  .min(1, "캠페인 설명은 필수입니다")
  .max(200, "설명은 200자 이내여야 합니다");

export const segmentIdSchema = z.number().int().nullable();
export const messageTemplateIdSchema = z.number().int();

// HH:mm 또는 HH:mm:ss
const TIME_RE = /^([01]\d|2[0-3]):([0-5]\d)(?::[0-5]\d)?$/;
export const repeatTimeSchema = z
  .string()
  .regex(TIME_RE, "시각은 HH:mm 형식이어야 합니다");

// ---------- Schedule discriminated union ----------

export const immediateScheduleSchema = z.object({
  type: z.literal("immediate"),
});

export const scheduledScheduleSchema = z.object({
  type: z.literal("scheduled"),
  /** ISO datetime (KST 기반 datetime-local 입력을 toUtcIso 로 변환하여 전달). */
  scheduledAt: z.string().min(1, "발송 시각은 필수입니다"),
});

export const recurringScheduleSchema = z.object({
  type: z.literal("recurring"),
  repeatType: RepeatTypeSchema,
  repeatTime: repeatTimeSchema,
  /** WEEKLY 일 때만 사용. "MON,WED,FRI" 형식. */
  repeatDayOfWeek: z.string().optional(),
  /** MONTHLY 일 때만 사용. 1~31. */
  repeatDayOfMonth: z.number().int().min(1).max(31).optional(),
  /** 목표 반복 발송 횟수 (>=1). */
  recurringCount: z
    .number()
    .int()
    .min(1, "반복 횟수는 1 이상이어야 합니다"),
});

const baseScheduleUnion = z.discriminatedUnion("type", [
  immediateScheduleSchema,
  scheduledScheduleSchema,
  recurringScheduleSchema,
]);

/**
 * Schedule 종합 스키마 — discriminated union + superRefine.
 * scheduled: 과거 시각 차단(CAMPAIGN_008).
 * recurring: WEEKLY 요일 필수/검증 + MONTHLY 일자 필수.
 */
export const ScheduleSchema = baseScheduleUnion.superRefine((d, ctx) => {
  if (d.type === "scheduled") {
    const dt = new Date(d.scheduledAt);
    if (Number.isNaN(dt.getTime())) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: "올바른 일시 형식이 아닙니다",
        path: ["scheduledAt"],
      });
      return;
    }
    if (dt.getTime() <= Date.now()) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: "발송 예정 시각은 현재보다 과거일 수 없습니다",
        path: ["scheduledAt"],
      });
    }
  }
  if (d.type === "recurring") {
    if (d.repeatType === "WEEKLY") {
      const dow = (d.repeatDayOfWeek ?? "").trim();
      if (!dow) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: "주간 반복 시 요일은 필수입니다",
          path: ["repeatDayOfWeek"],
        });
      } else {
        const tokens = dow.split(",").map((t) => t.trim().toUpperCase());
        for (const t of tokens) {
          if (!(DAY_OF_WEEK_LIST as readonly string[]).includes(t)) {
            ctx.addIssue({
              code: z.ZodIssueCode.custom,
              message: "요일 값은 MON~SUN 만 사용할 수 있습니다",
              path: ["repeatDayOfWeek"],
            });
            break;
          }
        }
      }
    }
    if (d.repeatType === "MONTHLY") {
      if (d.repeatDayOfMonth == null) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: "월간 반복 일자는 1~31 사이여야 합니다",
          path: ["repeatDayOfMonth"],
        });
      }
    }
  }
});
export type ScheduleInput = z.infer<typeof ScheduleSchema>;

// ---------- 단계별 부분 검증 ----------

export const step1Schema = z.object({
  campaignName: nameSchema,
  campaignType: CampaignTypeSchema,
  campaignDescription: descSchema,
});
export type Step1Input = z.infer<typeof step1Schema>;

export const step2Schema = z.object({
  channel: ChannelSchema,
});
export type Step2Input = z.infer<typeof step2Schema>;

export const step3Schema = z.object({
  segmentId: segmentIdSchema,
});
export type Step3Input = z.infer<typeof step3Schema>;

export const step4Schema = z.object({
  schedule: ScheduleSchema,
});
export type Step4Input = z.infer<typeof step4Schema>;

export const step5Schema = z.object({
  messageTemplateId: messageTemplateIdSchema,
});
export type Step5Input = z.infer<typeof step5Schema>;

// 6단계는 종합 검증 — createCampaignSchema 와 동일.

// ---------- 종합 폼 스키마 ----------

export const createCampaignSchema = z.object({
  campaignName: nameSchema,
  campaignDescription: descSchema,
  campaignType: CampaignTypeSchema,
  channel: ChannelSchema,
  segmentId: segmentIdSchema,
  messageTemplateId: messageTemplateIdSchema,
  schedule: ScheduleSchema,
  /** true 면 mock-fallback 측에서 DRAFT 상태 유지. false 면 그래도 DRAFT (start 는 별도 API). */
  isTempSave: z.boolean(),
});
export type CreateCampaignInput = z.infer<typeof createCampaignSchema>;

/** PUT — DRAFT 상태에서만 호출 가능. 동일 필드 사용. */
export const updateCampaignSchema = createCampaignSchema;
export type UpdateCampaignInput = z.infer<typeof updateCampaignSchema>;

// ---------- 헬퍼 ----------

/**
 * `<input type="datetime-local">` 의 로컬 타임 문자열 ("2026-05-10T14:30") 을
 * UTC ISO 문자열 ("2026-05-10T05:30:00.000Z") 로 변환.
 *
 * 브라우저의 Date 생성자는 타임존 정보가 없는 문자열을 로컬로 해석하므로
 * 그대로 toISOString() 하면 UTC 변환된 ISO 가 반환된다.
 */
export function toUtcIso(localDateTime: string): string {
  if (!localDateTime) return localDateTime;
  const d = new Date(localDateTime);
  if (Number.isNaN(d.getTime())) return localDateTime;
  return d.toISOString();
}

/**
 * UTC ISO 를 datetime-local 입력에 적합한 KST 로컬 타임 문자열로 변환.
 * "2026-05-10T05:30:00.000Z" → "2026-05-10T14:30"
 */
export function fromUtcIsoToLocal(iso: string): string {
  if (!iso) return iso;
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  const yyyy = d.getFullYear();
  const mm = String(d.getMonth() + 1).padStart(2, "0");
  const dd = String(d.getDate()).padStart(2, "0");
  const hh = String(d.getHours()).padStart(2, "0");
  const mi = String(d.getMinutes()).padStart(2, "0");
  return `${yyyy}-${mm}-${dd}T${hh}:${mi}`;
}
