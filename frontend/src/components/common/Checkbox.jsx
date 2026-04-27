import React from "react";
import { Icon } from "./Icon.jsx";

export default function Checkbox({ checked, onChange, label, style }) {
  return (
    <label
      style={{
        display: "inline-flex", alignItems: "center", gap: 8,
        cursor: "pointer", userSelect: "none",
        ...style,
      }}
    >
      <span
        onClick={() => onChange && onChange(!checked)}
        style={{
          width: 16, height: 16, borderRadius: 4,
          border: `1.5px solid ${checked ? "#3b5bdb" : "#cbd5e1"}`,
          background: checked ? "#3b5bdb" : "#fff",
          display: "inline-flex", alignItems: "center", justifyContent: "center",
          transition: "all 120ms",
        }}
      >
        {checked && <span style={{ color: "#fff" }}>{Icon.check(11)}</span>}
      </span>
      {label && <span style={{ fontSize: 12, color: "#334155" }}>{label}</span>}
    </label>
  );
}
