import React from "react";
import { UIBus } from "../../lib/ui-bus";

const TONES = {
  info:    { bg: "#1f2937", color: "#fff", accent: "#3b5bdb" },
  success: { bg: "#15803d", color: "#fff", accent: "#4ade80" },
  error:   { bg: "#b91c1c", color: "#fff", accent: "#fca5a5" },
  warn:    { bg: "#b45309", color: "#fff", accent: "#fbbf24" },
};

export default function ToastHost() {
  const [toasts, setToasts] = React.useState([]);

  React.useEffect(
    () =>
      UIBus.on((e) => {
        if (e.type !== "toast") return;
        const id = Math.random().toString(36).slice(2);
        setToasts((t) => [...t, { id, ...e }]);
        setTimeout(() => setToasts((t) => t.filter((x) => x.id !== id)), 3000);
      }),
    []
  );

  return (
    <div
      style={{
        position: "fixed", bottom: 24, left: "50%",
        transform: "translateX(-50%)",
        zIndex: 10000,
        display: "flex", flexDirection: "column",
        gap: 8, alignItems: "center",
        pointerEvents: "none",
      }}
    >
      {toasts.map((t) => {
        const tone = TONES[t.kind] || TONES.info;
        return (
          <div
            key={t.id}
            style={{
              padding: "10px 16px 10px 14px",
              background: tone.bg, color: tone.color,
              borderRadius: 10,
              fontSize: 13, fontWeight: 500,
              boxShadow: "0 8px 32px -4px rgba(0,0,0,.25)",
              display: "flex", alignItems: "center", gap: 10,
              borderLeft: `3px solid ${tone.accent}`,
              animation: "toastIn 220ms cubic-bezier(.2,.7,.3,1)",
            }}
          >
            {t.msg}
          </div>
        );
      })}
      <style>{`@keyframes toastIn { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: translateY(0); } }`}</style>
    </div>
  );
}
