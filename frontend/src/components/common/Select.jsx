import React from "react";
import { Icon } from "./Icon.jsx";

export default function Select({ label, children, options, value, style, onChange, width, ...rest }) {
  return (
    <label style={{ display: "block", width: width ?? undefined, ...style }}>
      {label && (
        <div style={{ fontSize: 12, fontWeight: 500, color: "#334155", marginBottom: 6 }}>
          {label}
        </div>
      )}
      <div style={{ position: "relative" }}>
        <select
          value={value}
          onChange={(e) => onChange?.(e.target.value)}
          style={{
            width: "100%", height: 36, padding: "0 32px 0 12px",
            fontFamily: "inherit", fontSize: 13, color: "#1f2937",
            background: "#fff", border: "1px solid #e2e8f0", borderRadius: 8,
            outline: "none", appearance: "none", boxSizing: "border-box",
            cursor: "pointer",
          }}
          {...rest}
        >
          {options
            ? options.map((o) => (
                <option key={o.value} value={o.value}>
                  {o.label}
                </option>
              ))
            : children}
        </select>
        <span
          style={{
            position: "absolute", right: 10, top: "50%",
            transform: "translateY(-50%)",
            color: "#94a3b8", pointerEvents: "none",
          }}
        >
          {Icon.chevronDown(14)}
        </span>
      </div>
    </label>
  );
}
