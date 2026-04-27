import React from "react";

export default function Input({ label, help, error, style, ...rest }) {
  return (
    <label style={{ display: "block", ...style }}>
      {label && (
        <div style={{ fontSize: 12, fontWeight: 500, color: "#334155", marginBottom: 6 }}>
          {label}
        </div>
      )}
      <input
        {...rest}
        style={{
          width: "100%", height: 36, padding: "0 12px",
          fontFamily: "inherit", fontSize: 13, color: "#1f2937",
          background: "#fff",
          border: `1px solid ${error ? "#ef4444" : "#e2e8f0"}`,
          borderRadius: 8,
          outline: "none", boxSizing: "border-box",
        }}
      />
      {help && (
        <div
          style={{
            marginTop: 6, fontSize: 11,
            color: error ? "#ef4444" : "#94a3b8",
          }}
        >
          {help}
        </div>
      )}
    </label>
  );
}
