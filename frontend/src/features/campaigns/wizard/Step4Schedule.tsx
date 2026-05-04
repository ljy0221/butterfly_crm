import { buildCronPreview, describeRecurring } from "@/lib/cron-preview";
import {
  DAY_OF_WEEK_LIST,
  fromUtcIsoToLocal,
  type RepeatType,
  type ScheduleInput,
} from "@/schemas/campaign";
import type { FieldErrorMap, WizardDraft } from "./wizardReducer";

/**
 * Step 4 — 발송 일정.
 * SendMode 라디오 3종 (IMMEDIATE / SCHEDULED / RECURRING).
 * - SCHEDULED: native datetime-local. 클라가 toUtcIso 로 변환 후 저장 시점에만 ISO 사용.
 * - RECURRING: 백엔드 RecurringCronGenerator JS 포팅으로 미리보기.
 */

export interface Step4ScheduleProps {
  draft: WizardDraft;
  errors: FieldErrorMap;
  onChangeScheduleType: (type: ScheduleInput["type"]) => void;
  onChangeScheduledAt: (value: string) => void; // local datetime-local 형식
  onChangeRepeatType: (value: RepeatType) => void;
  onChangeRepeatTime: (value: string) => void;
  onChangeRepeatDayOfWeek: (value: string) => void;
  onChangeRepeatDayOfMonth: (value: number | undefined) => void;
  onChangeRecurringCount: (value: number) => void;
}

const DOW_LABEL: Record<string, string> = {
  MON: "월",
  TUE: "화",
  WED: "수",
  THU: "목",
  FRI: "금",
  SAT: "토",
  SUN: "일",
};

export default function Step4Schedule({
  draft,
  errors,
  onChangeScheduleType,
  onChangeScheduledAt,
  onChangeRepeatType,
  onChangeRepeatTime,
  onChangeRepeatDayOfWeek,
  onChangeRepeatDayOfMonth,
  onChangeRecurringCount,
}: Step4ScheduleProps) {
  const schedule = draft.schedule;

  const scheduledLocalValue =
    schedule.type === "scheduled"
      ? // 사용자가 입력 중이면 그대로 보여주고, 아니면 ISO → local
        schedule.scheduledAt && schedule.scheduledAt.includes("Z")
        ? fromUtcIsoToLocal(schedule.scheduledAt)
        : schedule.scheduledAt
      : "";

  const scheduledMin = (() => {
    const d = new Date(Date.now() + 60_000);
    const yyyy = d.getFullYear();
    const mm = String(d.getMonth() + 1).padStart(2, "0");
    const dd = String(d.getDate()).padStart(2, "0");
    const hh = String(d.getHours()).padStart(2, "0");
    const mi = String(d.getMinutes()).padStart(2, "0");
    return `${yyyy}-${mm}-${dd}T${hh}:${mi}`;
  })();

  const recurringPreview =
    schedule.type === "recurring"
      ? buildCronPreview({
          repeatType: schedule.repeatType,
          repeatTime: schedule.repeatTime,
          repeatDayOfWeek: schedule.repeatDayOfWeek,
          repeatDayOfMonth: schedule.repeatDayOfMonth,
        })
      : { ok: false, cron: "", message: "" };

  const recurringLabel =
    schedule.type === "recurring"
      ? describeRecurring({
          repeatType: schedule.repeatType,
          repeatTime: schedule.repeatTime,
          repeatDayOfWeek: schedule.repeatDayOfWeek,
          repeatDayOfMonth: schedule.repeatDayOfMonth,
        })
      : "";

  return (
    <div>
      <h2 style={titleStyle}>발송 일정</h2>
      <p style={subtitleStyle}>
        즉시 발송, 특정 시각 예약, 반복 발송 중 하나를 선택하세요.
      </p>

      <div role="radiogroup" aria-label="발송 모드 선택" style={{ display: "grid", gap: 10, gridTemplateColumns: "repeat(3, 1fr)", marginBottom: 18 }}>
        <ModeCard
          active={schedule.type === "immediate"}
          title="즉시 발송"
          description="저장 후 곧바로 발송"
          onSelect={() => onChangeScheduleType("immediate")}
        />
        <ModeCard
          active={schedule.type === "scheduled"}
          title="예약 발송"
          description="특정 일시에 1회 발송"
          onSelect={() => onChangeScheduleType("scheduled")}
        />
        <ModeCard
          active={schedule.type === "recurring"}
          title="반복 발송"
          description="DAILY / WEEKLY / MONTHLY"
          onSelect={() => onChangeScheduleType("recurring")}
        />
      </div>

      {schedule.type === "scheduled" && (
        <Field label="발송 일시" required error={errors.scheduledAt}>
          <input
            type="datetime-local"
            value={scheduledLocalValue}
            min={scheduledMin}
            onChange={(e) => onChangeScheduledAt(e.target.value)}
            style={inputStyle(!!errors.scheduledAt)}
          />
          <div style={{ fontSize: 11, color: "#94a3b8", marginTop: 4 }}>
            현재 브라우저 로컬 시간대로 입력하세요. 저장 시 UTC로 변환됩니다.
          </div>
        </Field>
      )}

      {schedule.type === "recurring" && (
        <>
          <Field label="반복 유형" required error={errors.repeatType}>
            <div role="radiogroup" style={{ display: "flex", gap: 8 }}>
              {(["DAILY", "WEEKLY", "MONTHLY"] as RepeatType[]).map((rt) => (
                <button
                  key={rt}
                  type="button"
                  role="radio"
                  aria-checked={schedule.repeatType === rt}
                  onClick={() => onChangeRepeatType(rt)}
                  style={{
                    padding: "8px 14px",
                    borderRadius: 8,
                    border: "1px solid",
                    borderColor: schedule.repeatType === rt ? "#3b5bdb" : "#e2e8f0",
                    background: schedule.repeatType === rt ? "rgba(59,91,219,0.06)" : "#fff",
                    color: schedule.repeatType === rt ? "#3b5bdb" : "#1f2937",
                    fontSize: 12.5,
                    fontWeight: 600,
                    cursor: "pointer",
                    fontFamily: "inherit",
                  }}
                >
                  {rt === "DAILY" ? "매일" : rt === "WEEKLY" ? "매주" : "매월"}
                </button>
              ))}
            </div>
          </Field>

          {schedule.repeatType === "WEEKLY" && (
            <Field label="요일 (1개 이상)" required error={errors.repeatDayOfWeek}>
              <div role="group" style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
                {DAY_OF_WEEK_LIST.map((d) => {
                  const tokens: string[] = (schedule.repeatDayOfWeek ?? "")
                    .split(",")
                    .map((t: string) => t.trim().toUpperCase())
                    .filter(Boolean);
                  const active = tokens.includes(d);
                  return (
                    <button
                      key={d}
                      type="button"
                      role="checkbox"
                      aria-pressed={active}
                      onClick={() => {
                        const next: string[] = active
                          ? tokens.filter((t: string) => t !== d)
                          : [...tokens, d];
                        // 요일 순서 정렬
                        const ORDER = DAY_OF_WEEK_LIST as readonly string[];
                        next.sort((a: string, b: string) => ORDER.indexOf(a) - ORDER.indexOf(b));
                        onChangeRepeatDayOfWeek(next.join(","));
                      }}
                      style={{
                        width: 36,
                        height: 36,
                        borderRadius: 8,
                        border: "1px solid",
                        borderColor: active ? "#3b5bdb" : "#e2e8f0",
                        background: active ? "rgba(59,91,219,0.08)" : "#fff",
                        color: active ? "#3b5bdb" : "#334155",
                        fontWeight: 600,
                        fontSize: 12,
                        cursor: "pointer",
                        fontFamily: "inherit",
                      }}
                    >
                      {DOW_LABEL[d] ?? d}
                    </button>
                  );
                })}
              </div>
            </Field>
          )}

          {schedule.repeatType === "MONTHLY" && (
            <Field label="발송 일자 (1~31)" required error={errors.repeatDayOfMonth}>
              <input
                type="number"
                min={1}
                max={31}
                value={schedule.repeatDayOfMonth ?? ""}
                onChange={(e) => {
                  const v = e.target.value;
                  if (v === "") onChangeRepeatDayOfMonth(undefined);
                  else onChangeRepeatDayOfMonth(Number(v));
                }}
                style={{ ...inputStyle(!!errors.repeatDayOfMonth), maxWidth: 120 }}
              />
            </Field>
          )}

          <Field label="발송 시각" required error={errors.repeatTime}>
            <input
              type="time"
              value={schedule.repeatTime}
              onChange={(e) => onChangeRepeatTime(e.target.value)}
              style={{ ...inputStyle(!!errors.repeatTime), maxWidth: 160 }}
            />
          </Field>

          <Field label="목표 반복 횟수" required error={errors.recurringCount}>
            <input
              type="number"
              min={1}
              value={schedule.recurringCount}
              onChange={(e) => onChangeRecurringCount(Number(e.target.value || 1))}
              style={{ ...inputStyle(!!errors.recurringCount), maxWidth: 120 }}
            />
            <div style={{ fontSize: 11, color: "#94a3b8", marginTop: 4 }}>
              이 횟수에 도달하면 캠페인이 자동으로 종료됩니다.
            </div>
          </Field>

          <div
            style={{
              padding: 12,
              background: "#f8fafc",
              border: "1px solid #e2e8f0",
              borderRadius: 8,
              marginTop: 8,
            }}
            aria-live="polite"
          >
            <div style={{ fontSize: 11, fontWeight: 600, color: "#94a3b8", marginBottom: 6, textTransform: "uppercase", letterSpacing: "0.05em" }}>
              미리보기
            </div>
            {recurringLabel && (
              <div style={{ fontSize: 13, fontWeight: 600, color: "#1f2937", marginBottom: 6 }}>{recurringLabel}</div>
            )}
            <div
              style={{
                fontSize: 11.5,
                fontFamily: "var(--font-mono)",
                color: recurringPreview.ok ? "#3b5bdb" : "#ef4444",
              }}
            >
              {recurringPreview.ok ? `cron: ${recurringPreview.cron}` : recurringPreview.message ?? "검증 실패"}
            </div>
          </div>
        </>
      )}
    </div>
  );
}

function ModeCard({
  active,
  title,
  description,
  onSelect,
}: {
  active: boolean;
  title: string;
  description: string;
  onSelect: () => void;
}) {
  return (
    <button
      type="button"
      role="radio"
      aria-checked={active}
      onClick={onSelect}
      style={{
        padding: 14,
        borderRadius: 10,
        border: "1px solid",
        borderColor: active ? "#3b5bdb" : "#e2e8f0",
        background: active ? "rgba(59,91,219,0.06)" : "#fff",
        cursor: "pointer",
        textAlign: "left",
        fontFamily: "inherit",
      }}
    >
      <div style={{ fontSize: 13, fontWeight: 700, color: active ? "#3b5bdb" : "#1f2937", marginBottom: 4 }}>
        {title}
      </div>
      <div style={{ fontSize: 11.5, color: "#64748b" }}>{description}</div>
    </button>
  );
}

function Field({
  label,
  required,
  error,
  children,
}: {
  label: string;
  required?: boolean;
  error?: string;
  children: React.ReactNode;
}) {
  return (
    <div style={{ marginBottom: 16 }}>
      <div style={{ fontSize: 12, fontWeight: 500, color: "#334155", marginBottom: 6 }}>
        {label}
        {required && <span style={{ color: "#ef4444", marginLeft: 4 }}>*</span>}
      </div>
      {children}
      {error && (
        <div style={{ marginTop: 6, fontSize: 11, color: "#ef4444" }} role="alert">
          {error}
        </div>
      )}
    </div>
  );
}

function inputStyle(invalid: boolean): React.CSSProperties {
  return {
    width: "100%",
    height: 36,
    padding: "0 12px",
    fontFamily: "inherit",
    fontSize: 13,
    color: "#1f2937",
    background: "#fff",
    border: `1px solid ${invalid ? "#ef4444" : "#e2e8f0"}`,
    borderRadius: 8,
    outline: "none",
    boxSizing: "border-box",
  };
}

const titleStyle: React.CSSProperties = {
  margin: 0,
  marginBottom: 6,
  fontSize: 18,
  fontWeight: 700,
  color: "#1f2937",
  fontFamily: "var(--font-display)",
};

const subtitleStyle: React.CSSProperties = {
  margin: 0,
  marginBottom: 20,
  fontSize: 13,
  color: "#64748b",
  lineHeight: 1.5,
};
