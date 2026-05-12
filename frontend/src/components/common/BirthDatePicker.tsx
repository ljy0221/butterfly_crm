import { useState } from "react";

interface Props {
  value: string;
  onChange: (v: string) => void;
  error?: string;
}

const CURRENT_YEAR = new Date().getFullYear();
const YEARS = Array.from({ length: 100 }, (_, i) => CURRENT_YEAR - i);
const MONTHS = Array.from({ length: 12 }, (_, i) => i + 1);

function daysInMonth(year: number, month: number) {
  return new Date(year, month, 0).getDate();
}

export default function BirthDatePicker({ value, onChange, error }: Props) {
  const parsed = value ? value.split("-").map(Number) : [0, 0, 0];
  const [y, setY] = useState(parsed[0] || 0);
  const [m, setM] = useState(parsed[1] || 0);
  const [d, setD] = useState(parsed[2] || 0);

  const days =
    y && m
      ? Array.from({ length: daysInMonth(y, m) }, (_, i) => i + 1)
      : Array.from({ length: 31 }, (_, i) => i + 1);

  const emit = (ny: number, nm: number, nd: number) => {
    if (!ny || !nm || !nd) return;
    const safeDay = Math.min(nd, daysInMonth(ny, nm));
    onChange(
      `${ny}-${String(nm).padStart(2, "0")}-${String(safeDay).padStart(2, "0")}`,
    );
  };

  const handleYear  = (ny: number) => { setY(ny); emit(ny, m, d); };
  const handleMonth = (nm: number) => { setM(nm); emit(y, nm, d); };
  const handleDay   = (nd: number) => { setD(nd); emit(y, m, nd); };

  const borderColor = error ? "#ef4444" : "#e2e8f0";
  const selectStyle: React.CSSProperties = {
    height: 36, padding: "0 28px 0 10px",
    fontFamily: "inherit", fontSize: 13,
    color: y || m || d ? "#1f2937" : "#94a3b8",
    background: "#fff", border: `1px solid ${borderColor}`,
    borderRadius: 8, outline: "none",
    appearance: "none", boxSizing: "border-box",
    cursor: "pointer", width: "100%",
  };

  return (
    <div>
      <div style={{ fontSize: 12, fontWeight: 500, color: "#334155", marginBottom: 6 }}>
        생년월일
      </div>
      <div style={{ display: "grid", gridTemplateColumns: "1.4fr 1fr 1fr", gap: 6 }}>
        <div style={{ position: "relative" }}>
          <select style={selectStyle} value={y || ""} onChange={(e) => handleYear(Number(e.target.value))}>
            <option value="">년도</option>
            {YEARS.map((yr) => <option key={yr} value={yr}>{yr}년</option>)}
          </select>
          <ChevronDown />
        </div>
        <div style={{ position: "relative" }}>
          <select style={selectStyle} value={m || ""} onChange={(e) => handleMonth(Number(e.target.value))}>
            <option value="">월</option>
            {MONTHS.map((mo) => <option key={mo} value={mo}>{mo}월</option>)}
          </select>
          <ChevronDown />
        </div>
        <div style={{ position: "relative" }}>
          <select style={selectStyle} value={d || ""} onChange={(e) => handleDay(Number(e.target.value))}>
            <option value="">일</option>
            {days.map((dy) => <option key={dy} value={dy}>{dy}일</option>)}
          </select>
          <ChevronDown />
        </div>
      </div>
      {error && (
        <div style={{ marginTop: 6, fontSize: 11, color: "#ef4444" }}>{error}</div>
      )}
    </div>
  );
}

function ChevronDown() {
  return (
    <span style={{ position: "absolute", right: 8, top: "50%", transform: "translateY(-50%)", color: "#94a3b8", pointerEvents: "none" }}>
      <svg width="12" height="12" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="2">
        <path d="M4 6l4 4 4-4" />
      </svg>
    </span>
  );
}
