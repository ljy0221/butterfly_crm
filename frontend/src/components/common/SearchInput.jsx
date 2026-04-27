import React from "react";
import { Icon } from "./Icon.jsx";

export default function SearchInput({ placeholder, value, onChange, width = 320 }) {
  return (
    <div
      style={{
        display: "flex", alignItems: "center", gap: 8,
        height: 32, padding: "0 12px",
        border: "1px solid #e2e8f0", borderRadius: 8,
        background: "#fff",
        maxWidth: width, flex: 1,
      }}
    >
      <span style={{ color: "#94a3b8" }}>{Icon.search(13)}</span>
      <input
        value={value || ""}
        onChange={(e) => onChange && onChange(e.target.value)}
        placeholder={placeholder}
        style={{
          flex: 1, border: 0, outline: 0, background: "transparent",
          fontFamily: "inherit", fontSize: 12,
        }}
      />
      {value && (
        <span
          style={{ color: "#94a3b8", cursor: "pointer" }}
          onClick={() => onChange("")}
        >
          {Icon.x(12)}
        </span>
      )}
    </div>
  );
}
