import { useState, useRef, useEffect, useCallback } from "react";

// ── helpers ────────────────────────────────────────────────────────────────

function pad(n: number) {
  return String(n).padStart(2, "0");
}

function parseHHmm(v: string) {
  const m = /^(\d{2}):(\d{2})$/.exec(v);
  if (!m) return null;
  return { h: +m[1], min: +m[2] };
}

function toHHmm(h24: number, min: number) {
  return `${pad(h24)}:${pad(min)}`;
}

// ── constants ──────────────────────────────────────────────────────────────

const HOURS = Array.from({ length: 12 }, (_, i) => i + 1); // 1‥12
const MINUTES = Array.from({ length: 60 }, (_, i) => i);   // 0‥59
const COL_W = 68;
const ITEM_H = 44;
const COL_H = 264; // 6 rows visible

// ── TimeColumn ─────────────────────────────────────────────────────────────

function TimeColumn({
  items,
  selected,
  onSelect,
  fmt,
  noBorderLeft = false,
}: {
  items: number[];
  selected: number;
  onSelect: (v: number) => void;
  fmt: (v: number) => string;
  noBorderLeft?: boolean;
}) {
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!ref.current) return;
    const idx = items.indexOf(selected);
    ref.current.scrollTop = Math.max(0, idx * ITEM_H);
  }, [selected, items]);

  return (
    <div
      ref={ref}
      style={{
        width: COL_W,
        height: COL_H,
        overflowY: "auto",
        scrollbarWidth: "none",
        borderLeft: noBorderLeft ? "none" : "1px solid #f1f5f9",
      }}
    >
      {items.map((item) => {
        const active = item === selected;
        return (
          <div
            key={item}
            role="option"
            aria-selected={active}
            onClick={() => onSelect(item)}
            style={{
              height: ITEM_H,
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              fontSize: 14,
              fontFamily: "var(--font-mono, monospace)",
              fontWeight: active ? 700 : 400,
              color: active ? "#3b5bdb" : "#1f2937",
              background: active ? "rgba(59,91,219,0.1)" : "transparent",
              cursor: "pointer",
              userSelect: "none",
              transition: "background 80ms",
            }}
            onMouseEnter={(e) => {
              if (!active) e.currentTarget.style.background = "#f8fafc";
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.background = active
                ? "rgba(59,91,219,0.1)"
                : "transparent";
            }}
          >
            {fmt(item)}
          </div>
        );
      })}
    </div>
  );
}

// ── AmPmColumn ─────────────────────────────────────────────────────────────

function AmPmColumn({
  selected,
  onSelect,
}: {
  selected: "AM" | "PM";
  onSelect: (v: "AM" | "PM") => void;
}) {
  return (
    <div
      style={{
        width: COL_W,
        height: COL_H,
        borderLeft: "1px solid #f1f5f9",
        display: "flex",
        flexDirection: "column",
        justifyContent: "center",
        gap: 4,
      }}
    >
      {(["AM", "PM"] as const).map((ap) => {
        const active = ap === selected;
        return (
          <div
            key={ap}
            role="option"
            aria-selected={active}
            onClick={() => onSelect(ap)}
            style={{
              height: ITEM_H,
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              fontSize: 13,
              fontWeight: active ? 700 : 400,
              color: active ? "#3b5bdb" : "#1f2937",
              background: active ? "rgba(59,91,219,0.1)" : "transparent",
              cursor: "pointer",
              userSelect: "none",
              transition: "background 80ms",
            }}
            onMouseEnter={(e) => {
              if (!active) e.currentTarget.style.background = "#f8fafc";
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.background = active
                ? "rgba(59,91,219,0.1)"
                : "transparent";
            }}
          >
            {ap === "AM" ? "오전" : "오후"}
          </div>
        );
      })}
    </div>
  );
}

// ── TimePicker ─────────────────────────────────────────────────────────────

export interface TimePickerProps {
  value: string; // "HH:mm" 24시간제
  onChange: (v: string) => void;
  invalid?: boolean;
  style?: React.CSSProperties;
}

export default function TimePicker({
  value,
  onChange,
  invalid = false,
  style,
}: TimePickerProps) {
  const p = parseHHmm(value);

  const initH24 = p?.h ?? 9;
  const initAmPm: "AM" | "PM" = initH24 < 12 ? "AM" : "PM";
  const initHour12 = initH24 % 12 || 12;

  const [open, setOpen] = useState(false);
  const [selHour, setSelHour] = useState(initHour12);
  const [selMin, setSelMin] = useState(p?.min ?? 0);
  const [selAmPm, setSelAmPm] = useState<"AM" | "PM">(initAmPm);

  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const handler = (e: MouseEvent) => {
      if (!containerRef.current?.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, [open]);

  const emit = useCallback(
    (h12: number, min: number, ampm: "AM" | "PM") => {
      const h24 =
        ampm === "AM" ? (h12 === 12 ? 0 : h12) : h12 === 12 ? 12 : h12 + 12;
      onChange(toHHmm(h24, min));
    },
    [onChange],
  );

  const pickHour = (h: number) => { setSelHour(h); emit(h, selMin, selAmPm); };
  const pickMin  = (min: number) => { setSelMin(min); emit(selHour, min, selAmPm); };
  const pickAmPm = (ap: "AM" | "PM") => { setSelAmPm(ap); emit(selHour, selMin, ap); };

  // 트리거 레이블
  let label = "시간을 선택하세요";
  if (value) {
    const dp = parseHHmm(value);
    if (dp) {
      const h12 = dp.h % 12 || 12;
      const ap = dp.h < 12 ? "오전" : "오후";
      label = `${ap} ${pad(h12)}:${pad(dp.min)}`;
    }
  }

  return (
    <div ref={containerRef} style={{ position: "relative", maxWidth: 200, ...style }}>
      {/* Trigger */}
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        style={{
          width: "100%",
          height: 36,
          padding: "0 12px",
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          gap: 8,
          fontFamily: "inherit",
          fontSize: 13,
          color: value ? "#1f2937" : "#94a3b8",
          background: "#fff",
          border: `1px solid ${invalid ? "#ef4444" : open ? "#3b5bdb" : "#e2e8f0"}`,
          borderRadius: 8,
          cursor: "pointer",
          boxSizing: "border-box",
          outline: "none",
          transition: "border-color 120ms",
        }}
      >
        <span style={{ fontFamily: value ? "var(--font-mono, monospace)" : "inherit" }}>
          {label}
        </span>
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ flexShrink: 0, color: "#94a3b8" }}>
          <circle cx="12" cy="12" r="10" />
          <polyline points="12 6 12 12 16 14" />
        </svg>
      </button>

      {/* Popover */}
      {open && (
        <div
          style={{
            position: "absolute",
            top: 40,
            left: 0,
            zIndex: 1000,
            background: "#fff",
            border: "1px solid #e2e8f0",
            borderRadius: 12,
            boxShadow: "0 8px 32px rgba(0,0,0,0.12)",
            display: "flex",
            flexDirection: "column",
            overflow: "hidden",
          }}
        >
          {/* 컬럼 헤더 — DateTimePicker와 동일한 스타일 */}
          <div style={{ display: "flex", borderBottom: "1px solid #f1f5f9" }}>
            {["시", "분", ""].map((lbl, idx) => (
              <div
                key={idx}
                style={{
                  width: COL_W,
                  textAlign: "center",
                  fontSize: 11,
                  color: "#94a3b8",
                  fontWeight: 600,
                  padding: "8px 0",
                  borderLeft: idx > 0 ? "1px solid #f1f5f9" : "none",
                }}
              >
                {lbl}
              </div>
            ))}
          </div>

          {/* 스크롤 컬럼 */}
          <div style={{ display: "flex" }}>
            <TimeColumn items={HOURS} selected={selHour} onSelect={pickHour} fmt={pad} noBorderLeft />
            <TimeColumn items={MINUTES} selected={selMin} onSelect={pickMin} fmt={pad} />
            <AmPmColumn selected={selAmPm} onSelect={pickAmPm} />
          </div>

          {/* 확인 버튼 */}
          <div style={{ borderTop: "1px solid #e2e8f0", padding: "10px 16px", display: "flex", justifyContent: "flex-end" }}>
            <button
              type="button"
              onClick={() => setOpen(false)}
              style={{
                padding: "7px 20px",
                borderRadius: 8,
                border: "none",
                background: "#3b5bdb",
                color: "#fff",
                fontSize: 13,
                fontWeight: 600,
                cursor: "pointer",
                fontFamily: "inherit",
              }}
            >
              확인
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
