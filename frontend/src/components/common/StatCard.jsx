import React from "react";

const TREND_COLOR = { up: "#16a34a", down: "#ef4444", flat: "#64748b" };
const TREND_ARROW = { up: "▲", down: "▼", flat: "◆" };

export default function StatCard({ label, value, delta, trend = "up", sub }) {
  return (
    <div
      style={{
        flex: 1, minWidth: 0, padding: "18px 20px",
        background: "#fff", border: "1px solid #e2e8f0", borderRadius: 12,
      }}
    >
      <div style={{ fontSize: 12, fontWeight: 500, color: "#64748b" }}>{label}</div>
      <div
        style={{
          fontSize: 28, fontWeight: 700, color: "#1f2937",
          fontFamily: "var(--font-display)",
          marginTop: 8,
          fontVariantNumeric: "tabular-nums",
          letterSpacing: "-0.02em",
          lineHeight: 1.1,
        }}
      >
        {value}
      </div>
      {delta && (
        <div
          style={{
            fontSize: 11, fontWeight: 600,
            color: TREND_COLOR[trend] || "#64748b",
            marginTop: 8,
            display: "flex", alignItems: "center", gap: 4,
          }}
        >
          <span>{TREND_ARROW[trend] || "◆"}</span>
          <span>{delta}</span>
          {sub && <span style={{ color: "#94a3b8", fontWeight: 400, marginLeft: 4 }}>{sub}</span>}
        </div>
      )}
    </div>
  );
}
