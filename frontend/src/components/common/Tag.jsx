import React from "react";

const TONES = {
  default: { bg: "rgba(100,116,139,.10)", color: "#475569", dot: "#64748b" },
  success: { bg: "rgba(22,163,74,.10)",   color: "#15803d", dot: "#16a34a" },
  warning: { bg: "rgba(245,158,11,.14)",  color: "#b45309", dot: "#f59e0b" },
  error:   { bg: "rgba(239,68,68,.10)",   color: "#b91c1c", dot: "#ef4444" },
  info:    { bg: "rgba(59,91,219,.10)",   color: "#3b5bdb", dot: "#3b5bdb" },
  dark:    { bg: "#182034",               color: "#fff",    dot: "#637fff" },
  soft:    { bg: "#f1f5f9",               color: "#334155", dot: "#64748b" },
};

export default function Tag({ tone = "default", children, style, dot = false }) {
  const t = TONES[tone];
  return (
    <span
      style={{
        display: "inline-flex", alignItems: "center", gap: 6,
        height: 22, padding: "0 8px", borderRadius: 6,
        fontSize: 11, fontWeight: 600, letterSpacing: "-0.005em",
        background: t.bg, color: t.color,
        ...style,
      }}
    >
      {dot && <span style={{ width: 6, height: 6, borderRadius: 999, background: t.dot }} />}
      {children}
    </span>
  );
}
