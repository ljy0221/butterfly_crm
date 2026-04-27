import React from "react";

export default function Toggle({ checked, onChange }) {
  return (
    <button
      onClick={() => onChange && onChange(!checked)}
      style={{
        width: 34, height: 20, padding: 0, border: 0, borderRadius: 999,
        background: checked ? "#3b5bdb" : "#cbd5e1",
        position: "relative", cursor: "pointer",
        transition: "background 150ms",
      }}
    >
      <span
        style={{
          position: "absolute", top: 2, left: checked ? 16 : 2,
          width: 16, height: 16, borderRadius: 999,
          background: "#fff",
          boxShadow: "0 1px 2px rgba(0,0,0,.25)",
          transition: "left 150ms",
        }}
      />
    </button>
  );
}
