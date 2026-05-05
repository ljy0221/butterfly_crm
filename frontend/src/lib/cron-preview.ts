import type { RepeatType } from "@/schemas/campaign";

/**
 * Sprint 6 — RecurringCronGenerator JS 포팅 (미리보기 전용).
 *
 * 백엔드 com.b101.butterfly.campaign.campaign.domain.RecurringCronGenerator 를
 * 그대로 옮겼다. 6필드 Spring cron 형식.
 *  - DAILY:   "0 {minute} {hour} * * *"
 *  - WEEKLY:  "0 {minute} {hour} * * MON,WED,..."
 *  - MONTHLY: "0 {minute} {hour} {dayOfMonth} * *"
 *
 * 주의: 위저드 미리보기/표시 전용. 실제 직렬화는 백엔드(또는 mock-fallback) 가
 * type/period/repeatType 등 필드 기반으로 자체 산출한다.
 */

const ALLOWED_DAYS_OF_WEEK = new Set([
  "MON",
  "TUE",
  "WED",
  "THU",
  "FRI",
  "SAT",
  "SUN",
]);

export interface CronPreviewInput {
  repeatType: RepeatType;
  /** "HH:mm" 또는 "HH:mm:ss" */
  repeatTime?: string;
  /** "MON,WED" 등 — WEEKLY 일 때만 사용 */
  repeatDayOfWeek?: string;
  /** 1~31 — MONTHLY 일 때만 사용 */
  repeatDayOfMonth?: number;
}

export interface CronPreviewResult {
  ok: boolean;
  cron?: string;
  message?: string;
}

function parseTime(repeatTime: string | undefined): {
  ok: boolean;
  minute?: number;
  hour?: number;
  message?: string;
} {
  if (!repeatTime) {
    return { ok: false, message: "반복 발송 시각은 필수입니다" };
  }
  const m = repeatTime.match(/^([01]\d|2[0-3]):([0-5]\d)(?::[0-5]\d)?$/);
  if (!m) {
    return { ok: false, message: "시각 형식이 올바르지 않습니다" };
  }
  return { ok: true, hour: Number(m[1]), minute: Number(m[2]) };
}

function normalizeDayOfWeek(repeatDayOfWeek: string | undefined): {
  ok: boolean;
  value?: string;
  message?: string;
} {
  if (!repeatDayOfWeek || repeatDayOfWeek.trim() === "") {
    return { ok: false, message: "요일은 필수입니다" };
  }
  const tokens = repeatDayOfWeek.split(",").map((t) => t.trim().toUpperCase());
  for (const t of tokens) {
    if (!ALLOWED_DAYS_OF_WEEK.has(t)) {
      return { ok: false, message: "요일 값은 MON~SUN 만 사용 가능합니다" };
    }
  }
  return { ok: true, value: tokens.join(",") };
}

function validateDayOfMonth(repeatDayOfMonth: number | undefined): {
  ok: boolean;
  value?: number;
  message?: string;
} {
  if (
    repeatDayOfMonth == null ||
    !Number.isInteger(repeatDayOfMonth) ||
    repeatDayOfMonth < 1 ||
    repeatDayOfMonth > 31
  ) {
    return { ok: false, message: "월간 반복 일자는 1~31 사이여야 합니다" };
  }
  return { ok: true, value: repeatDayOfMonth };
}

/**
 * 백엔드 RecurringCronGenerator.toCron 동등 산출.
 * 검증 실패 시 ok=false + message 반환 (throw 하지 않음 — 미리보기 텍스트로 사용).
 */
export function buildCronPreview(input: CronPreviewInput): CronPreviewResult {
  const time = parseTime(input.repeatTime);
  if (!time.ok) return { ok: false, message: time.message };

  const minute = time.minute as number;
  const hour = time.hour as number;

  switch (input.repeatType) {
    case "DAILY":
      return { ok: true, cron: `0 ${minute} ${hour} * * *` };
    case "WEEKLY": {
      const dow = normalizeDayOfWeek(input.repeatDayOfWeek);
      if (!dow.ok) return { ok: false, message: dow.message };
      return { ok: true, cron: `0 ${minute} ${hour} * * ${dow.value}` };
    }
    case "MONTHLY": {
      const dom = validateDayOfMonth(input.repeatDayOfMonth);
      if (!dom.ok) return { ok: false, message: dom.message };
      return { ok: true, cron: `0 ${minute} ${hour} ${dom.value} * *` };
    }
    default:
      return { ok: false, message: "지원하지 않는 반복 유형입니다" };
  }
}

/**
 * 사람이 읽기 좋은 한국어 미리보기 문자열.
 *  - DAILY 14:30 → "매일 14:30"
 *  - WEEKLY MON,WED 14:30 → "매주 월·수요일 14:30"
 *  - MONTHLY 15 14:30 → "매월 15일 14:30"
 */
export function describeRecurring(input: CronPreviewInput): string {
  if (!input.repeatTime) return "";
  const time = input.repeatTime.slice(0, 5);
  if (input.repeatType === "DAILY") return `매일 ${time}`;
  if (input.repeatType === "WEEKLY") {
    const dow = (input.repeatDayOfWeek ?? "").trim();
    if (!dow) return `매주 ${time}`;
    const labels = dow
      .split(",")
      .map((t) => t.trim().toUpperCase())
      .map((t) => DAY_LABELS[t] ?? t)
      .join("·");
    return `매주 ${labels}요일 ${time}`;
  }
  if (input.repeatType === "MONTHLY") {
    const dom = input.repeatDayOfMonth;
    if (dom == null) return `매월 ${time}`;
    return `매월 ${dom}일 ${time}`;
  }
  return "";
}

const DAY_LABELS: Record<string, string> = {
  MON: "월",
  TUE: "화",
  WED: "수",
  THU: "목",
  FRI: "금",
  SAT: "토",
  SUN: "일",
};
