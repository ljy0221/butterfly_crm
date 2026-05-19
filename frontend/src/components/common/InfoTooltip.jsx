import React from "react";

export default function InfoTooltip({ message }) {
  const [visible, setVisible] = React.useState(false);
  if (!message) return null;

  return (
    <span
      style={{ position: "relative", display: "inline-flex", alignItems: "center" }}
      onMouseEnter={() => setVisible(true)}
      onMouseLeave={() => setVisible(false)}
    >
      <span
        style={{
          width: 16, height: 16, borderRadius: "50%",
          background: "#94a3b8", color: "#fff",
          fontSize: 10, fontWeight: 700,
          display: "inline-flex", alignItems: "center", justifyContent: "center",
          cursor: "default", userSelect: "none", flexShrink: 0,
        }}
      >
        ?
      </span>
      {visible && (
        <span
          style={{
            position: "absolute",
            bottom: "calc(100% + 6px)",
            right: 0,
            background: "#1e293b", color: "#f8fafc",
            fontSize: 12, fontWeight: 500,
            padding: "6px 10px",
            borderRadius: 6,
            whiteSpace: "nowrap",
            pointerEvents: "none",
            zIndex: 9999,
            boxShadow: "0 2px 8px rgba(0,0,0,0.18)",
          }}
        >
          {message}
          <span
            style={{
              position: "absolute",
              top: "100%", right: 4,
              border: "5px solid transparent",
              borderTopColor: "#1e293b",
            }}
          />
        </span>
      )}
    </span>
  );
}
