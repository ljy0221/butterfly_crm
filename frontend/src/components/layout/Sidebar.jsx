import React from "react";
import { Icon } from "../common/Icon.jsx";
import Avatar from "../common/Avatar.jsx";
import { CRM_DATA } from "../../data/mock.js";
import { useAuthStore } from "@/stores/auth";

export default function Sidebar({ active, onNavigate, collapsed }) {
  // NAV(메뉴 구조)는 mock 그대로, USER/PROJECT는 실 store에서 가져온다.
  const { NAV } = CRM_DATA;
  const member = useAuthStore((s) => s.member);
  const w = collapsed ? 64 : 240;

  const userName = member?.name ?? "Guest";
  const userRole = member?.role ?? "";

  return (
    <aside
      style={{
        width: w, height: "100%", background: "#182034",
        position: "relative", overflow: "hidden", flex: "none",
        display: "flex", flexDirection: "column",
        transition: "width 180ms var(--ease-standard)",
      }}
    >

      {/* Brand mark */}
      <div
        style={{
          padding: collapsed ? "20px 0" : "22px 20px 14px",
          display: "flex", alignItems: "center", gap: 10,
          justifyContent: collapsed ? "center" : "flex-start",
        }}
      >
        <div
          style={{
            width: 28, height: 28, borderRadius: 8, background: "#3b5bdb",
            display: "flex", alignItems: "center", justifyContent: "center",
            flex: "none", color: "#fff",
            fontFamily: "var(--font-display)", fontWeight: 700, fontSize: 15,
          }}
        >
          B
        </div>
        {!collapsed && (
          <div>
            <div
              style={{
                color: "#fff", fontWeight: 700, fontSize: 15,
                fontFamily: "var(--font-display)", letterSpacing: "-0.01em",
              }}
            >
              Butterfly
            </div>
            <div style={{ color: "#94a3b8", fontSize: 10, marginTop: 1 }}>
              CRM Operator Console
            </div>
          </div>
        )}
      </div>


      <div
        style={{
          height: 1,
          background: "rgba(255,255,255,.06)",
          margin: collapsed ? "8px 16px" : "4px 16px 10px",
        }}
      />

      {/* Nav items */}
      <nav
        style={{
          flex: 1, overflowY: "auto",
          padding: "0 12px",
          display: "flex", flexDirection: "column", gap: 1,
        }}
      >
        {NAV.map((n) => {
          const isActive = active === n.key;
          return (
            <div
              key={n.key}
              onClick={() => onNavigate(n.key)}
              title={collapsed ? n.label : undefined}
              style={{
                position: "relative",
                display: "flex", alignItems: "center", gap: 10,
                padding: collapsed ? "10px 0" : "9px 10px",
                justifyContent: collapsed ? "center" : "flex-start",
                fontSize: 13, fontWeight: isActive ? 600 : 400,
                color: isActive ? "#fff" : "#94a3b8",
                background: isActive ? "rgba(59,91,219,.18)" : "transparent",
                borderRadius: 8, cursor: "pointer", userSelect: "none",
                transition: "background 120ms",
              }}
            >
              {isActive && !collapsed && (
                <span
                  style={{
                    position: "absolute", left: -12, top: "50%",
                    transform: "translateY(-50%)",
                    width: 3, height: 18,
                    borderRadius: 2, background: "#637fff",
                  }}
                />
              )}
              <span style={{ color: isActive ? "#fff" : "#94a3b8", display: "flex" }}>
                {Icon[n.icon](18)}
              </span>
              {!collapsed && <span>{n.label}</span>}
            </div>
          );
        })}
      </nav>

      {/* Account footer */}
      {!collapsed && (
        <div
          style={{
            margin: "10px 16px 16px", padding: 12,
            background: "rgba(255,255,255,.04)",
            border: "1px solid rgba(255,255,255,.06)",
            borderRadius: 10,
            display: "flex", alignItems: "center", gap: 10,
          }}
        >
          <Avatar name={userName} size={30} />
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ color: "#fff", fontSize: 12, fontWeight: 600 }}>{userName}</div>
            <div style={{ color: "#94a3b8", fontSize: 10 }}>{userRole || "—"}</div>
          </div>
          <span style={{ color: "#94a3b8" }}>{Icon.chevronDown(12)}</span>
        </div>
      )}
    </aside>
  );
}
