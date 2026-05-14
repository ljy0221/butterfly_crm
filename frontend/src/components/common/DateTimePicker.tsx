import { useState, useRef, useEffect, useCallback } from "react";

// ── helpers ────────────────────────────────────────────────────────────────

function pad(n: number) {
  return String(n).padStart(2, "0");
}

function parseLocalDt(v: string) {
  if (!v) return null;
  const m = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(v);
  if (!m) return null;
  return { y: +m[1], mo: +m[2] - 1, d: +m[3], h: +m[4], min: +m[5] };
}

function toLocalDt(y: number, mo: number, d: number, h: number, min: number) {
  return `${y}-${pad(mo + 1)}-${pad(d)}T${pad(h)}:${pad(min)}`;
}

function daysInMonth(y: number, m: number) {
  return new Date(y, m + 1, 0).getDate();
}

function firstDow(y: number, m: number) {
  return new Date(y, m, 1).getDay(); // 0=Sun
}

// ── constants ──────────────────────────────────────────────────────────────

const HOURS = Array.from({ length: 12 }, (_, i) => i + 1);          // 1‥12
const MINUTES = Array.from({ length: 12 }, (_, i) => i * 5);        // 0,5,10‥55
const KO_DOW = ["일", "월", "화", "수", "목", "금", "토"];

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
  const ITEM_H = 44;

  useEffect(() => {
    if (!ref.current) return;
    const idx = items.indexOf(selected);
    ref.current.scrollTop = Math.max(0, idx * ITEM_H);
  }, [selected, items]);

  return (
    <div
      ref={ref}
      style={{
        width: 68,
        height: 308,
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
        width: 68,
        height: 308,
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
              height: 44,
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

// ── DateTimePicker ─────────────────────────────────────────────────────────

export interface DateTimePickerProps {
  value: string;          // "YYYY-MM-DDTHH:mm" (local)
  min?: string;           // "YYYY-MM-DDTHH:mm" (local)
  onChange: (v: string) => void;
  invalid?: boolean;
  style?: React.CSSProperties;
}

export default function DateTimePicker({
  value,
  min,
  onChange,
  invalid = false,
  style,
}: DateTimePickerProps) {
  const now = new Date();
  const p = parseLocalDt(value);

  const initH24 = p?.h ?? now.getHours();
  const initAmPm: "AM" | "PM" = initH24 < 12 ? "AM" : "PM";
  const initHour12 = initH24 % 12 || 12;

  const [open, setOpen] = useState(false);
  const [viewYear, setViewYear] = useState(p?.y ?? now.getFullYear());
  const [viewMonth, setViewMonth] = useState(p?.mo ?? now.getMonth());
  const [selDate, setSelDate] = useState<{ y: number; mo: number; d: number } | null>(
    p ? { y: p.y, mo: p.mo, d: p.d } : null,
  );
  const [selHour, setSelHour] = useState(initHour12);
  const [selMin, setSelMin] = useState(Math.round((p?.min ?? 0) / 5) * 5 % 60);
  const [selAmPm, setSelAmPm] = useState<"AM" | "PM">(initAmPm);

  const containerRef = useRef<HTMLDivElement>(null);

  // Close on outside click
  useEffect(() => {
    if (!open) return;
    const handler = (e: MouseEvent) => {
      if (!containerRef.current?.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, [open]);

  const emit = useCallback(
    (
      date: { y: number; mo: number; d: number } | null,
      h12: number,
      min: number,
      ampm: "AM" | "PM",
    ) => {
      if (!date) return;
      const h24 =
        ampm === "AM" ? (h12 === 12 ? 0 : h12) : h12 === 12 ? 12 : h12 + 12;
      onChange(toLocalDt(date.y, date.mo, date.d, h24, min));
    },
    [onChange],
  );

  const pickDate = (y: number, mo: number, d: number) => {
    const nd = { y, mo, d };
    setSelDate(nd);
    emit(nd, selHour, selMin, selAmPm);
  };

  const pickHour = (h: number) => {
    setSelHour(h);
    emit(selDate, h, selMin, selAmPm);
  };

  const pickMin = (min: number) => {
    setSelMin(min);
    emit(selDate, selHour, min, selAmPm);
  };

  const pickAmPm = (ap: "AM" | "PM") => {
    setSelAmPm(ap);
    emit(selDate, selHour, selMin, ap);
  };

  const clearAll = () => {
    setSelDate(null);
    onChange("");
    setOpen(false);
  };

  const goToday = () => {
    const t = new Date();
    setViewYear(t.getFullYear());
    setViewMonth(t.getMonth());
    pickDate(t.getFullYear(), t.getMonth(), t.getDate());
  };

  // Calendar cells (6 rows × 7 cols = 42)
  const firstDay = firstDow(viewYear, viewMonth);
  const daysCount = daysInMonth(viewYear, viewMonth);
  const prevDays = daysInMonth(
    viewMonth === 0 ? viewYear - 1 : viewYear,
    viewMonth === 0 ? 11 : viewMonth - 1,
  );

  const cells: { day: number; kind: "prev" | "cur" | "next" }[] = [];
  for (let i = 0; i < firstDay; i++)
    cells.push({ day: prevDays - firstDay + 1 + i, kind: "prev" });
  for (let d = 1; d <= daysCount; d++)
    cells.push({ day: d, kind: "cur" });
  for (let d = 1; d <= 42 - cells.length; d++)
    cells.push({ day: d, kind: "next" });

  const minP = parseLocalDt(min ?? "");

  // Display label
  let label = "날짜/시간을 선택하세요";
  if (value) {
    const dp = parseLocalDt(value);
    if (dp) {
      const h12 = dp.h % 12 || 12;
      const ap = dp.h < 12 ? "오전" : "오후";
      label = `${dp.y}-${pad(dp.mo + 1)}-${pad(dp.d)}  ${ap} ${pad(h12)}:${pad(dp.min)}`;
    }
  }

  const prevMonth = () => {
    if (viewMonth === 0) { setViewYear((y) => y - 1); setViewMonth(11); }
    else setViewMonth((m) => m - 1);
  };
  const nextMonth = () => {
    if (viewMonth === 11) { setViewYear((y) => y + 1); setViewMonth(0); }
    else setViewMonth((m) => m + 1);
  };

  return (
    <div ref={containerRef} style={{ position: "relative", width: "100%", ...style }}>
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
        {/* Calendar icon */}
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ flexShrink: 0, color: "#94a3b8" }}>
          <rect x="3" y="4" width="18" height="18" rx="2" ry="2"/>
          <line x1="16" y1="2" x2="16" y2="6"/>
          <line x1="8" y1="2" x2="8" y2="6"/>
          <line x1="3" y1="10" x2="21" y2="10"/>
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
          {/* ── 상단: 캘린더 + 시간 ── */}
          <div style={{ display: "flex" }}>
            {/* Calendar pane */}
            <div style={{ padding: "16px 16px 14px", width: 280, boxSizing: "border-box" }}>
              {/* Month nav */}
              <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 12 }}>
                <button type="button" onClick={prevMonth} style={navBtn}>↑</button>
                <span style={{ fontSize: 13, fontWeight: 700, color: "#1f2937" }}>
                  {viewYear}년 {viewMonth + 1}월
                </span>
                <button type="button" onClick={nextMonth} style={navBtn}>↓</button>
              </div>

              {/* DOW headers */}
              <div style={{ display: "grid", gridTemplateColumns: "repeat(7, 1fr)", marginBottom: 4 }}>
                {KO_DOW.map((d) => (
                  <div key={d} style={{ textAlign: "center", fontSize: 11, color: "#94a3b8", fontWeight: 600, padding: "4px 0" }}>
                    {d}
                  </div>
                ))}
              </div>

              {/* Day cells */}
              <div style={{ display: "grid", gridTemplateColumns: "repeat(7, 1fr)", gap: 2 }}>
                {cells.map((cell, i) => {
                  const isCur = cell.kind === "cur";
                  const isSel =
                    isCur &&
                    selDate?.y === viewYear &&
                    selDate?.mo === viewMonth &&
                    selDate?.d === cell.day;
                  const isToday =
                    isCur &&
                    now.getFullYear() === viewYear &&
                    now.getMonth() === viewMonth &&
                    now.getDate() === cell.day;

                  let disabled = !isCur;
                  if (!disabled && minP) {
                    const cellTime = new Date(viewYear, viewMonth, cell.day, 23, 59);
                    const minTime = new Date(minP.y, minP.mo, minP.d, 0, 0);
                    if (cellTime < minTime) disabled = true;
                  }

                  return (
                    <button
                      key={i}
                      type="button"
                      disabled={disabled}
                      onClick={() => isCur && !disabled && pickDate(viewYear, viewMonth, cell.day)}
                      style={{
                        width: "100%",
                        aspectRatio: "1",
                        border: "none",
                        borderRadius: "50%",
                        background: isSel ? "#3b5bdb" : "transparent",
                        color: isSel ? "#fff" : isCur ? (disabled ? "#cbd5e1" : "#1f2937") : "#d1d5db",
                        fontWeight: isSel ? 700 : 400,
                        fontSize: 12.5,
                        cursor: isCur && !disabled ? "pointer" : "default",
                        fontFamily: "inherit",
                        padding: 0,
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "center",
                      }}
                    >
                      {cell.day}
                    </button>
                  );
                })}
              </div>
            </div>

            {/* Time pane */}
            <div style={{ display: "flex", flexDirection: "column", borderLeft: "1px solid #e2e8f0" }}>
              {/* 컬럼 헤더 */}
              <div style={{ display: "flex", borderBottom: "1px solid #f1f5f9" }}>
                {["시", "분", ""].map((label, idx) => (
                  <div
                    key={idx}
                    style={{
                      width: 68,
                      textAlign: "center",
                      fontSize: 11,
                      color: "#94a3b8",
                      fontWeight: 600,
                      padding: "8px 0",
                      borderLeft: idx > 0 ? "1px solid #f1f5f9" : "none",
                    }}
                  >
                    {label}
                  </div>
                ))}
              </div>
              {/* 스크롤 컬럼 */}
              <div style={{ display: "flex" }}>
                <TimeColumn items={HOURS} selected={selHour} onSelect={pickHour} fmt={pad} noBorderLeft />
                <TimeColumn items={MINUTES} selected={selMin} onSelect={pickMin} fmt={pad} />
                <AmPmColumn selected={selAmPm} onSelect={pickAmPm} />
              </div>
            </div>
          </div>

          {/* ── 공통 푸터: 삭제 + 확인 ── */}
          <div
            style={{
              borderTop: "1px solid #e2e8f0",
              padding: "10px 16px",
              display: "flex",
              justifyContent: "space-between",
              alignItems: "center",
            }}
          >
            <button type="button" onClick={clearAll} style={{ ...footerBtn, color: "#ef4444" }}>
              삭제
            </button>
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

// ── shared styles ──────────────────────────────────────────────────────────

const navBtn: React.CSSProperties = {
  width: 28,
  height: 28,
  border: "1px solid #e2e8f0",
  borderRadius: 6,
  background: "#fff",
  cursor: "pointer",
  fontSize: 12,
  display: "flex",
  alignItems: "center",
  justifyContent: "center",
  fontFamily: "inherit",
  color: "#64748b",
};

const footerBtn: React.CSSProperties = {
  background: "none",
  border: "none",
  fontSize: 12,
  fontWeight: 500,
  cursor: "pointer",
  fontFamily: "inherit",
  padding: "4px 8px",
};
