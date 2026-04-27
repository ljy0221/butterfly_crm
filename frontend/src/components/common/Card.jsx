import React from "react";

export default function Card({ children, style, padding = 20, title, action, subtitle }) {
  return (
    <div
      style={{
        background: "#fff",
        border: "1px solid #e2e8f0",
        borderRadius: 12,
        ...style,
      }}
    >
      {(title || action) && (
        <div
          style={{
            padding: "16px 20px",
            borderBottom: "1px solid #f1f5f9",
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
          }}
        >
          <div>
            <div
              style={{
                fontSize: 14, fontWeight: 700, color: "#1f2937",
                fontFamily: "var(--font-display)",
              }}
            >
              {title}
            </div>
            {subtitle && (
              <div style={{ marginTop: 2, fontSize: 12, color: "#64748b" }}>{subtitle}</div>
            )}
          </div>
          {action}
        </div>
      )}
      <div style={{ padding: title ? "16px 20px 20px" : padding }}>{children}</div>
    </div>
  );
}
