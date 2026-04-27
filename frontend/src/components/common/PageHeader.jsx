import React from "react";
import { Icon } from "./Icon.jsx";

export default function PageHeader({ title, subtitle, actions, back, onBack }) {
  return (
    <div
      style={{
        display: "flex", alignItems: "flex-start", justifyContent: "space-between",
        marginBottom: 24, gap: 20,
      }}
    >
      <div>
        {back && (
          <button
            onClick={onBack}
            style={{
              display: "inline-flex", alignItems: "center", gap: 6,
              marginBottom: 10,
              border: 0, background: "transparent",
              color: "#64748b", fontSize: 12, fontFamily: "inherit",
              cursor: "pointer",
            }}
          >
            {Icon.back(12)} {back}
          </button>
        )}
        <h1
          style={{
            margin: 0, fontSize: 24, fontWeight: 700,
            color: "#1f2937",
            fontFamily: "var(--font-display)",
            letterSpacing: "-0.015em",
          }}
        >
          {title}
        </h1>
        {subtitle && (
          <div style={{ marginTop: 6, color: "#64748b", fontSize: 13 }}>{subtitle}</div>
        )}
      </div>
      {actions && (
        <div style={{ display: "flex", gap: 8, flex: "none" }}>{actions}</div>
      )}
    </div>
  );
}
