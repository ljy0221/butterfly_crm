import React from "react";
import { toast } from "../../lib/ui-bus";

const INITIAL = [];

const KIND_COLOR = { success: "#16a34a", warn: "#f59e0b", info: "#3b5bdb" };

export default function NotificationsPanel({ close, onNavigate }) {
  const [items, setItems] = React.useState(INITIAL);

  const markAllRead = () => {
    setItems((prev) => prev.map((i) => ({ ...i, unread: false })));
    toast("모든 알림을 읽음 처리했습니다", "success");
  };

  return (
    <div
      style={{
        position: "fixed", top: 60, right: 24,
        width: 380, background: "#fff", borderRadius: 14,
        boxShadow: "0 24px 64px -8px rgba(0,0,0,.25)",
        overflow: "hidden",
        maxHeight: "80vh",
        display: "flex", flexDirection: "column",
      }}
    >
      <div
        style={{
          padding: "16px 18px",
          borderBottom: "1px solid #f1f5f9",
          display: "flex", alignItems: "center", justifyContent: "space-between",
        }}
      >
        <div
          style={{
            fontSize: 15, fontWeight: 700, color: "#1f2937",
            fontFamily: "var(--font-display)",
          }}
        >
          알림
        </div>
        <span
          onClick={markAllRead}
          style={{
            fontSize: 11, color: "#3b5bdb",
            cursor: "pointer", fontWeight: 600,
          }}
        >
          모두 읽음
        </span>
      </div>

      <div style={{ overflowY: "auto", flex: 1 }}>
        {items.length === 0 && (
          <div style={{ padding: 48, textAlign: "center", color: "#94a3b8", fontSize: 13 }}>
            새 알림이 없습니다
          </div>
        )}
        {items.map((n) => (
          <div
            key={n.id}
            style={{
              padding: "14px 18px",
              borderBottom: "1px solid #f8fafc",
              display: "flex", gap: 12,
              cursor: "pointer",
              background: n.unread ? "#fafbfe" : "#fff",
            }}
            onClick={() => close()}
          >
            <span
              style={{
                width: 8, height: 8, borderRadius: 999,
                background: KIND_COLOR[n.kind] || "#3b5bdb",
                marginTop: 6, flex: "none",
              }}
            />
            <div style={{ flex: 1 }}>
              <div style={{ fontSize: 13, fontWeight: 600, color: "#1f2937" }}>{n.title}</div>
              <div style={{ fontSize: 12, color: "#64748b", marginTop: 3, lineHeight: 1.5 }}>
                {n.desc}
              </div>
              <div style={{ fontSize: 11, color: "#94a3b8", marginTop: 6 }}>{n.t}</div>
            </div>
          </div>
        ))}
      </div>

    </div>
  );
}
