import React from "react";
import { Icon } from "../common/Icon.jsx";
import Avatar from "../common/Avatar.jsx";
import { useAuthStore } from "@/stores/auth";
import { useProjectStore } from "@/stores/project";

const NAV = [
  { key: "대시보드",  label: "대시보드",       icon: "dashboard" },
  { key: "이벤트",   label: "이벤트",          icon: "events"    },
  { key: "고객",     label: "고객",            icon: "customers" },
  { key: "세그먼트", label: "세그먼트",         icon: "segments"  },
  { key: "캠페인",   label: "캠페인",           icon: "campaigns" },
  { key: "크레딧",   label: "크레딧",           icon: "wallet", ownerOnly: true },
  { key: "자동화",   label: "자동화 플로우",    icon: "journey"   },
  { key: "분석",     label: "분석",            icon: "analytics" },
  { key: "설정",     label: "설정",            icon: "settings"  },
];

function roleBadgeStyle(role) {
  const map = {
    OWNER:  { background: "rgba(99,127,255,.2)",  color: "#637fff" },
    ADMIN:  { background: "rgba(225,29,72,.2)",   color: "#f87171" },
    EDITOR: { background: "rgba(5,150,105,.2)",   color: "#34d399" },
    VIEWER: { background: "rgba(148,163,184,.15)", color: "#94a3b8" },
  };
  return map[role] ?? map.VIEWER;
}

export default function Sidebar({ active, onNavigate, collapsed }) {
  const member = useAuthStore((s) => s.member);
  const projectRole = useProjectStore((s) => s.current?.role);
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
        {NAV.filter((n) => !n.ownerOnly || projectRole === "OWNER").map((n) => {
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
            margin: "10px 16px 16px", padding: "10px 12px",
            background: "rgba(255,255,255,.04)",
            border: "1px solid rgba(255,255,255,.06)",
            borderRadius: 10,
            display: "flex", alignItems: "center", gap: 10,
          }}
        >
          <Avatar name={userName} size={30} />
          <div style={{ flex: 1, minWidth: 0, display: "flex", alignItems: "center", justifyContent: "center" }}>
            <div style={{ color: "#fff", fontSize: 12, fontWeight: 600, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
              {userName}
            </div>
          </div>
          {userRole && (
            <div style={{
              display: "inline-flex", alignItems: "center",
              fontSize: 12, fontWeight: 600, letterSpacing: "0.04em",
              padding: "2px 7px", borderRadius: 20, flexShrink: 0, lineHeight: 1,
              ...roleBadgeStyle(userRole),
            }}>
              {userRole}
            </div>
          )}
        </div>
      )}
    </aside>
  );
}
