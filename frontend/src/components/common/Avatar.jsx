import React from "react";

const COLORS = ["#dbe3fb", "#fde68a", "#c7f0db", "#fecaca", "#e9d5ff", "#cffafe"];
const TEXT_COLORS = ["#3b5bdb", "#b45309", "#15803d", "#b91c1c", "#7c3aed", "#0e7490"];

export default function Avatar({ name, size = 28, style }) {
  const initials = (name || "?").split(" ").map((p) => p[0]).join("").slice(0, 2).toUpperCase();
  let h = 0;
  for (const c of initials) h = (h * 31 + c.charCodeAt(0)) % 1000;
  const i = h % COLORS.length;
  return (
    <div
      style={{
        width: size, height: size, borderRadius: 999,
        background: COLORS[i], color: TEXT_COLORS[i],
        display: "inline-flex", alignItems: "center", justifyContent: "center",
        fontSize: size * 0.4, fontWeight: 700, lineHeight: 1, flex: "none",
        ...style,
      }}
    >
      {initials}
    </div>
  );
}
