import { useState, useEffect } from "react";
import type { Period } from "@/schemas/segment";

/**
 * Period (Relative / Absolute / Cumulative) 편집기.
 *
 * 백엔드 sealed Period:
 *  - relative: { type, days: 1~3650 }
 *  - absolute: { type, startDate: yyyy-MM-dd, endDate: yyyy-MM-dd }  (start <= end)
 *  - cumulative: { type } — 추가 필드 없음
 */

export interface PeriodEditorProps {
  value: Period;
  onChange: (next: Period) => void;
  disabled?: boolean;
}

const TABS: Array<{ value: Period["type"]; label: string }> = [
  { value: "relative", label: "최근 N일" },
  { value: "absolute", label: "기간 지정" },
  { value: "cumulative", label: "전체 기간" },
];

const inputStyle: React.CSSProperties = {
  height: 30,
  padding: "0 10px",
  border: "1px solid #e2e8f0",
  borderRadius: 6,
  background: "#fff",
  fontFamily: "inherit",
  fontSize: 12,
  color: "#1f2937",
  outline: "none",
  boxSizing: "border-box",
};

export default function PeriodEditor({
  value,
  onChange,
  disabled,
}: PeriodEditorProps) {
  // 입력 중 빈 문자열을 허용하기 위한 로컬 state
  const [localDays, setLocalDays] = useState(String(value.type === "relative" ? value.days : 30));

  // 외부에서 value.days가 바뀌면 동기화 (탭 전환 등)
  useEffect(() => {
    if (value.type === "relative") {
      setLocalDays(String(value.days));
    }
  }, [value]);

  const switchType = (next: Period["type"]): void => {
    if (next === value.type) return;
    if (next === "relative") {
      onChange({ type: "relative", days: 30 });
    } else if (next === "absolute") {
      const today = new Date();
      const past = new Date(today);
      past.setDate(today.getDate() - 30);
      onChange({
        type: "absolute",
        startDate: past.toISOString().slice(0, 10),
        endDate: today.toISOString().slice(0, 10),
      });
    } else {
      onChange({ type: "cumulative" });
    }
  };

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
      <div
        role="tablist"
        aria-label="기간 유형"
        style={{
          display: "inline-flex",
          gap: 0,
          padding: 3,
          background: "#f1f5f9",
          borderRadius: 7,
          alignSelf: "flex-start",
        }}
      >
        {TABS.map((t) => (
          <button
            key={t.value}
            type="button"
            role="tab"
            aria-selected={value.type === t.value}
            disabled={disabled}
            onClick={() => switchType(t.value)}
            style={{
              padding: "5px 12px",
              fontSize: 11,
              fontWeight: 700,
              border: 0,
              borderRadius: 5,
              background: value.type === t.value ? "#fff" : "transparent",
              color: value.type === t.value ? "#1f2937" : "#64748b",
              cursor: disabled ? "not-allowed" : "pointer",
              fontFamily: "inherit",
              boxShadow:
                value.type === t.value ? "0 1px 2px rgba(0,0,0,.06)" : "none",
            }}
          >
            {t.label}
          </button>
        ))}
      </div>

      {value.type === "relative" && (
        <div
          style={{ display: "flex", alignItems: "center", gap: 6, fontSize: 12 }}
        >
          <span style={{ color: "#64748b" }}>최근</span>
          <input
            type="number"
            min={1}
            max={3650}
            value={localDays}
            disabled={disabled}
            onChange={(e) => {
              const raw = e.target.value;
              setLocalDays(raw);
              const n = Number(raw);
              if (raw !== "" && Number.isFinite(n) && n >= 1) {
                onChange({ type: "relative", days: Math.min(3650, n) });
              }
            }}
            onBlur={() => {
              const n = Number(localDays);
              const clamped = !Number.isFinite(n) || n < 1 ? 1 : Math.min(3650, n);
              setLocalDays(String(clamped));
              onChange({ type: "relative", days: clamped });
            }}
            style={{ ...inputStyle, width: 80 }}
          />
          <span style={{ color: "#64748b" }}>일</span>
        </div>
      )}

      {value.type === "absolute" && (
        <div
          style={{
            display: "flex",
            alignItems: "center",
            gap: 6,
            flexWrap: "wrap",
            fontSize: 12,
          }}
        >
          <input
            type="date"
            value={value.startDate}
            disabled={disabled}
            onChange={(e) =>
              onChange({
                type: "absolute",
                startDate: e.target.value,
                endDate: value.endDate,
              })
            }
            style={inputStyle}
          />
          <span style={{ color: "#64748b" }}>~</span>
          <input
            type="date"
            value={value.endDate}
            disabled={disabled}
            onChange={(e) =>
              onChange({
                type: "absolute",
                startDate: value.startDate,
                endDate: e.target.value,
              })
            }
            style={inputStyle}
          />
          {value.startDate > value.endDate && (
            <span style={{ color: "#ef4444", fontSize: 11 }}>
              시작일이 종료일보다 늦을 수 없습니다
            </span>
          )}
        </div>
      )}

      {value.type === "cumulative" && (
        <div style={{ fontSize: 12, color: "#64748b" }}>
          서비스 전체 기간을 누적 평가합니다.
        </div>
      )}
    </div>
  );
}
